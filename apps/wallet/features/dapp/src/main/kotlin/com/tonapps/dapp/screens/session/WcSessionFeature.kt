package com.tonapps.dapp.screens.session

import android.content.Context
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.blockchain.model.DappConnectRequest
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.wallet.data.dapps.wc.WcRepository
import com.tonapps.wallet.localization.Localization
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.flow.mapLatestCatching
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal as JavaBigDecimal

sealed interface WcSessionEvent {
    data object Done : WcSessionEvent
    data class ShowError(val message: String) : WcSessionEvent
}

class WcSessionFeature(
    private val context: Context,
    val dappConnection: DappConnectRequest,
    private val oldAccountRepo: AccountRepository,
    private val accountRepo: McAccountRepository,
    private val wcRepo: WcRepository,
    private val settingsRepository: SettingsRepository,
) : AsyncViewModel() {

    private val relay = MviRelay<WcSessionEvent>()
    val events = relay.events

    val appInfo = dappConnection.info
    val isLoading = MutableStateFlow(false)

    val wallets = MutableStateFlow<List<McWalletEntity>>(emptyList())
    val selectedWallet = MutableStateFlow<McWalletEntity?>(null)
    val balances = MutableStateFlow<Map<String, CharSequence>>(emptyMap())

    val accounts: StateFlow<List<AccountEntity>?> = selectedWallet
        .mapLatestCatching {
            val walletId = it?.id ?: return@mapLatestCatching null
            val accounts = accountRepo.getCoinAccounts(walletId)
                .associateBy { account -> account.chain.caip2 }
            val resolved = dappConnection.chains.mapNotNull { chain ->
                accounts[chain.caip2] // TODO check if required
            }

            if (resolved.size != dappConnection.chains.size) {
                failWithRejection(context.getString(Localization.wc_error_chains_unavailable))
                return@mapLatestCatching null
            }

            resolved
        }
        .cacheState()

    init {
        bgScope.launch {
            val walletId = oldAccountRepo.getSelectedWalletId()
            if (walletId == null) {
                failWithRejection(context.getString(Localization.wc_error_no_wallet_selected))
                return@launch
            }

            val mcWallets = runCatching { accountRepo.getWallets() }.getOrNull()
            if (mcWallets == null) {
                failWithRejection(context.getString(Localization.wc_error_failed_to_approve))
                return@launch
            }

            val selected = mcWallets.firstOrNull { it.id == walletId }
            if (selected == null) {
                failWithRejection(context.getString(Localization.wc_error_multichain_only))
                return@launch
            }

            wallets.tryEmit(mcWallets)
            selectedWallet.tryEmit(selected)
            balances.tryEmit(loadBalances(mcWallets))
        }
    }

    fun selectWallet(wallet: McWalletEntity) {
        selectedWallet.tryEmit(wallet)
    }

    fun approve() {
        bgScope.launch {
            isLoading.tryEmit(true)
            try {
                val walletId = selectedWallet.value?.id
                    ?: throw IllegalStateException(context.getString(Localization.wc_error_no_wallet_selected))

                wcRepo.approveProposal(dappConnection.id, walletId, dappConnection.chains)
                    .getOrThrow()

                relay.emit(WcSessionEvent.Done)
            } catch (e: Throwable) {
                L.e(e)
                failWithRejection(e.message ?: context.getString(Localization.wc_error_failed_to_approve))
            } finally {
                isLoading.tryEmit(false)
            }
        }
    }

    fun reject() {
        bgScope.launch {
            isLoading.tryEmit(true)
            try {
                wcRepo.rejectProposal(dappConnection.id).getOrThrow()
                relay.emit(WcSessionEvent.Done)
            } catch (e: Throwable) {
                L.e(e)
                relay.emit(WcSessionEvent.ShowError(e.message ?: context.getString(Localization.wc_error_failed_to_reject)))
            } finally {
                isLoading.tryEmit(false)
            }
        }
    }

    private suspend fun failWithRejection(message: String) {
        runCatching { wcRepo.rejectProposal(dappConnection.id).getOrThrow() }.onFailure(L::e)
        relay.emit(WcSessionEvent.ShowError(message))
    }

    private suspend fun loadBalances(wallets: List<McWalletEntity>): Map<String, CharSequence> {
        if (settingsRepository.hiddenBalances) return emptyMap()
        val currency = settingsRepository.currency.code
        val hideDust = settingsRepository.hideDustAssets
        return wallets.mapNotNull { wallet ->
            val total = runCatching {
                accountRepo.getCachedAccounts(
                    walletId = wallet.id,
                    currency = currency,
                    hideDust = hideDust,
                )?.total
            }.getOrNull() ?: return@mapNotNull null
            wallet.id to formatFiat(currency, total)
        }.toMap()
    }

    private fun formatFiat(currency: String, total: BigDecimal): CharSequence {
        return CurrencyFormatter.formatFiat(
            currency = currency,
            value = JavaBigDecimal(total.toStringExpanded()),
        )
    }
}
