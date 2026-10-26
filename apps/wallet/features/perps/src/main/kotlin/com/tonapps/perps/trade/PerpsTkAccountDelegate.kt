package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpsAccountDelegate
import com.tonapps.chainkit.perpetual.PerpsAccountRegistration
import com.tonapps.chainkit.perpetual.PerpsAccountState
import com.tonapps.chainkit.perpetual.PerpsAccountSubmissionOutcome
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.LIGHTER_API_KEY_INDEX
import com.tonapps.perps.data.PerpsAccount
import com.tonapps.perps.data.PerpsLoadException
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.PerpsSendError
import com.tonapps.perps.data.PerpsSendReason
import com.tonapps.perps.data.PerpsSignedTx
import kotlinx.coroutines.delay
import kotlin.math.min

internal class PerpsTkAccountDelegate(
    private val walletId: String,
    private val repository: PerpsRepository,
    private val expectedPublicKey: String,
) : PerpsAccountDelegate {

    private var hadL2KeyBeforeSubmit: Boolean? = null

    override suspend fun accountState(): PerpsAccountState {
        val account = loadAccount()
        hadL2KeyBeforeSubmit = account.hasL2Key

        return account.toState(account.holdsOurKey() ?: account.hasL2Key)
    }

    override suspend fun nextNonce(accountIndex: Long, apiKeyIndex: Int): Long {
        if (apiKeyIndex != LIGHTER_API_KEY_INDEX) {
            throw IllegalStateException("perps: nonce is read for slot $LIGHTER_API_KEY_INDEX, asked for $apiKeyIndex")
        }
        val nonce = when (val result = repository.nextNonce(walletId)) {
            is KResult.Ok -> result.value
            is KResult.Err -> throw PerpsLoadException(result.value)
        }
        if (nonce.accountIndex != accountIndex) {
            throw IllegalStateException("perps: nonce is for account ${nonce.accountIndex}, expected $accountIndex")
        }

        return nonce.nonce
    }

    override suspend fun submitRegistration(
        registration: PerpsAccountRegistration,
    ): PerpsAccountSubmissionOutcome {
        val tx = PerpsSignedTx(
            txType = registration.txType,
            txInfo = registration.txInfo,
            txHash = registration.txHash,
        )

        return when (val result = repository.sendTransactions(walletId, listOf(tx))) {
            is KResult.Ok -> {
                val sent = result.value.singleOrNull()
                val accepted = sent != null &&
                    sent.txType == registration.txType &&
                    sent.txHash.removePrefix(HEX_PREFIX)
                        .equals(registration.txHash.removePrefix(HEX_PREFIX), ignoreCase = true)
                if (accepted) {
                    PerpsAccountSubmissionOutcome.Accepted
                } else {
                    PerpsAccountSubmissionOutcome.ProtocolFailure
                }
            }

            is KResult.Err -> {
                val error = result.value
                if (error is PerpsSendError.Refused && error.reason == PerpsSendReason.RESIGN_REQUIRED) {
                    PerpsAccountSubmissionOutcome.ResignRequired
                } else {
                    throw IllegalStateException("perps: ChangePubKey was refused ($error)")
                }
            }
        }
    }

    override suspend fun confirmRegistration(registration: PerpsAccountRegistration): PerpsAccountState {
        repeat(CONFIRM_ATTEMPTS) { attempt ->
            if (attempt > 0) {
                delay(min(CONFIRM_DELAY_MS shl min(attempt - 1, CONFIRM_MAX_SHIFT), CONFIRM_MAX_DELAY_MS))
            }
            val account = loadAccount()
            val ourKey = account.holdsOurKey()
            val registered = ourKey == true ||
                (ourKey == null && account.hasL2Key == true) ||
                (account.hasL2Key == true && hadL2KeyBeforeSubmit == false)
            if (registered) {
                return account.toState(true)
            }
        }

        throw IllegalStateException("perps: ChangePubKey was not confirmed for $walletId")
    }

    private suspend fun loadAccount(): PerpsAccount {
        return when (val result = repository.account(walletId)) {
            is KResult.Ok -> result.value
            is KResult.Err -> throw PerpsLoadException(result.value)
        }
    }

    private fun PerpsAccount.holdsOurKey(): Boolean? = holdsKey(expectedPublicKey)

    private fun PerpsAccount.toState(hasL2Key: Boolean?): PerpsAccountState {
        return PerpsAccountState(
            accountIndex = accountIndex,
            l1Address = l1Address,
            status = null,
            hasL2Key = hasL2Key,
            availableBalance = availableBalance,
        )
    }

    private companion object {
        const val CONFIRM_ATTEMPTS = 18
        const val CONFIRM_DELAY_MS = 500L
        const val CONFIRM_MAX_DELAY_MS = 4_000L
        const val CONFIRM_MAX_SHIFT = 3
    }
}

/** Whether [publicKey] is registered in the app's slot; null while the venue lists no keys, as for no account. */
internal fun PerpsAccount.holdsKey(publicKey: String): Boolean? {
    val expected = publicKey.removePrefix(HEX_PREFIX)
    return apiKeys?.any {
        it.apiKeyIndex == LIGHTER_API_KEY_INDEX &&
            it.publicKey.removePrefix(HEX_PREFIX).equals(expected, ignoreCase = true)
    }
}

private const val HEX_PREFIX = "0x"
