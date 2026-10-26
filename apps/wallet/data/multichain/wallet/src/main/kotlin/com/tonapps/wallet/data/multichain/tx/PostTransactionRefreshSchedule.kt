package com.tonapps.wallet.data.multichain.tx

import com.tonapps.async.Async
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.wallet.data.multichain.account.AccountsWithTotal
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.WALLET_ASSETS_PAGE_SIZE
import com.tonapps.wallet.data.multichain.realtime.McWalletRealtimeProvider
import com.tonapps.wallet.data.settings.SettingsRepository
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PostTransactionRefreshSchedule(
    private val accountRepo: McAccountRepository,
    private val settings: SettingsRepository,
    private val realtimeProvider: McWalletRealtimeProvider,
) {

    private val jobs = ConcurrentHashMap<Pair<String, Chain>, Job>()

    fun notifyTransactionSent(walletId: String, chain: Chain) {
        accountRepo.refresh()
        val job = Async.globalScope().launch {
            var elapsed = Duration.ZERO
            for (tick in postTransactionRefreshTicks(chain)) {
                delay(tick - elapsed)
                elapsed = tick
                if (!realtimeProvider.isSubscribed(walletId)) {
                    refreshAccountsAfterTransaction(walletId)
                }
            }
        }
        jobs.put(walletId to chain, job)?.cancel()
    }

    private suspend fun refreshAccountsAfterTransaction(walletId: String) {
        val previous = getCachedAccountsOrNull(walletId)
        val fresh = try {
            accountRepo.fetchAccounts(
                walletId = walletId,
                limit = WALLET_ASSETS_PAGE_SIZE,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        if (fresh.balances() != previous?.balances()) {
            accountRepo.refresh()
        }
    }

    private fun AccountsWithTotal.balances(): List<Pair<String, String>> {
        return accounts.map { it.data.id to it.balance.available }
    }

    private suspend fun getCachedAccountsOrNull(walletId: String): AccountsWithTotal? {
        return try {
            accountRepo.getCachedAccounts(walletId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun postTransactionRefreshTicks(chain: Chain): List<Duration> = when (chain) {
        is Chain.Ton -> listOf(5.seconds, 15.seconds, 45.seconds)
        is Chain.Tron -> listOf(30.seconds, 1.minutes, 2.minutes, 4.minutes)
        is Chain.Bitcoin -> listOf(1.minutes, 5.minutes, 10.minutes, 20.minutes, 30.minutes)
        is Chain.Ethereum -> listOf(15.seconds, 30.seconds, 1.minutes, 2.minutes)
        is Chain.Evm -> listOf(5.seconds, 15.seconds, 45.seconds, 2.minutes)
        else -> listOf(15.seconds, 30.seconds, 1.minutes, 2.minutes)
    }
}
