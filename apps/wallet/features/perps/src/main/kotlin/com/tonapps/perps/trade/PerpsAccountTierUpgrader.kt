package com.tonapps.perps.trade

import com.tonapps.log.L
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class PerpsAccountTierUpgrader(
    private val repository: PerpsRepository,
    private val perpetualRepository: PerpetualRepository,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    private val lock = Any()

    private val settledWallets = HashSet<String>()

    private val checkedAtMs = HashMap<String, Long>()

    private val upgrades = HashMap<String, Deferred<Unit>>()

    fun ensurePlusInBackground(walletId: String) {
        scope.launch { ensurePlus(walletId, Trigger.ROUTINE) }
    }

    suspend fun ensurePlusAfterActivation(walletId: String) {
        ensurePlusWithin(walletId, Trigger.ACTIVATION)
    }

    suspend fun ensurePlusBeforeTrading(walletId: String) {
        ensurePlusWithin(walletId, Trigger.ROUTINE)
    }

    private suspend fun ensurePlusWithin(walletId: String, trigger: Trigger) {
        val finished = withTimeoutOrNull(WAIT_MS) {
            ensurePlus(walletId, trigger)
        }
        if (finished == null) {
            L.w("perps: account tier did not settle in time for $walletId ($trigger)")
        }
    }

    private suspend fun ensurePlus(walletId: String, trigger: Trigger) {
        val upgrade = synchronized(lock) {
            if (walletId in settledWallets) {
                return
            }
            upgrades[walletId] ?: run {
                val now = nowMs()
                if (trigger == Trigger.ROUTINE &&
                    now - (checkedAtMs[walletId] ?: 0L) < RECHECK_INTERVAL_MS
                ) {
                    return
                }
                checkedAtMs[walletId] = now
                scope.async(start = CoroutineStart.LAZY) {
                    try {
                        upgradeWithRetries(walletId, trigger)
                    } finally {
                        synchronized(lock) { upgrades.remove(walletId) }
                    }
                }.also { upgrades[walletId] = it }
            }
        }
        upgrade.await()
    }

    private suspend fun upgradeWithRetries(walletId: String, trigger: Trigger) {
        val accountIndex = guarded { perpetualRepository.getAccount(walletId)?.accountIndex } ?: return
        repeat(ATTEMPTS) { attempt ->
            when (val result = guarded { upgrade(walletId, accountIndex) } ?: KResult.Err(PerpsError.Unknown)) {
                is KResult.Ok -> {
                    synchronized(lock) { settledWallets += walletId }
                    return
                }

                is KResult.Err -> {
                    if (result.value !in RETRYABLE || attempt == ATTEMPTS - 1) {
                        L.e("perps: tier upgrade failed for account $accountIndex ($trigger, ${result.value})")
                        return
                    }
                    delay(RETRY_DELAY_MS)
                }
            }
        }
    }

    private suspend fun upgrade(walletId: String, accountIndex: Long): KResult<Unit, PerpsError> {
        val tier = when (val result = repository.accountTierName(walletId)) {
            is KResult.Ok -> result.value.lowercase()
            is KResult.Err -> return KResult.Err(result.value)
        }
        if (tier != STANDARD_TIER) {
            L.d("perps: account $accountIndex is on $tier")
            return KResult.Ok(Unit)
        }
        val authorization = perpetualRepository.authorization(walletId, nowMs() / 1_000 + AUTH_SECONDS)
            ?: return KResult.Err(PerpsError.NoAccount)
        val changed = repository.changeAccountTier(walletId, authorization, PLUS_TIER)
        if (changed is KResult.Ok) {
            L.d("perps: account $accountIndex moved from $tier to $PLUS_TIER")
        }
        return changed
    }

    private suspend fun <T> guarded(block: suspend () -> T): T? {
        return try {
            block()
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e, "perps: tier check failed")
            null
        }
    }

    private enum class Trigger { ACTIVATION, ROUTINE }

    private companion object {
        const val STANDARD_TIER = "standard"
        const val PLUS_TIER = "plus"
        const val ATTEMPTS = 3
        const val RETRY_DELAY_MS = 2_000L
        const val RECHECK_INTERVAL_MS = 60_000L
        const val WAIT_MS = 3_000L
        const val AUTH_SECONDS = 600L

        val RETRYABLE = setOf(PerpsError.Network, PerpsError.ServiceUnavailable, PerpsError.Unknown)
    }
}
