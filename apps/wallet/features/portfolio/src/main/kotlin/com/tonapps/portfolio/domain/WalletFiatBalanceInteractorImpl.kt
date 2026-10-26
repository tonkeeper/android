package com.tonapps.portfolio.domain

import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.icu.Coins
import com.tonapps.legacy.assets.AssetsManager
import com.tonapps.portfolio.wallet.CommonWallet
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.WALLET_ASSETS_PAGE_SIZE
import com.tonapps.wallet.data.settings.SettingsRepository
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import java.math.BigDecimal as JavaBigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WalletFiatBalanceInteractorImpl(
    private val assetsManager: AssetsManager,
    private val mcAccountRepository: McAccountRepository,
    private val settingsRepository: SettingsRepository,
) : WalletFiatBalanceInteractor {

    override suspend fun getCachedBalance(
        wallet: CommonWallet,
    ): CharSequence? = withContext(Dispatchers.IO) {
        when (wallet) {
            is CommonWallet.Legacy -> safeCall {
                assetsManager.getCachedTotalBalance(
                    wallet = wallet.wallet,
                    currency = settingsRepository.currency,
                    sorted = true,
                )
            }?.let { formatLegacy(wallet.wallet, it) }

            is CommonWallet.Mc -> safeCall {
                mcAccountRepository.getCachedAccounts(wallet.id)?.total
            }?.let { formatMc(it) }
        }
    }

    override suspend fun fetchBalance(
        wallet: CommonWallet,
        refresh: Boolean,
    ): CharSequence? = withContext(Dispatchers.IO) {
        when (wallet) {
            is CommonWallet.Legacy -> safeCall {
                assetsManager.requestTotalBalance(
                    wallet = wallet.wallet,
                    currency = settingsRepository.currency,
                    refresh = refresh,
                    sorted = true,
                )
            }?.let { formatLegacy(wallet.wallet, it) }

            is CommonWallet.Mc -> safeCall {
                mcAccountRepository.fetchAccounts(
                    walletId = wallet.id,
                    limit = WALLET_ASSETS_PAGE_SIZE,
                ).total
            }?.let { formatMc(it) }
        }
    }

    private fun formatLegacy(wallet: WalletEntity, balance: Coins): CharSequence {
        val code = if (wallet.testnet) {
            WalletCurrency.TON.code
        } else {
            settingsRepository.currency.code
        }
        return CurrencyFormatter.formatFiat(code, balance)
    }

    private fun formatMc(total: BigDecimal): CharSequence {
        return CurrencyFormatter.formatFiat(
            currency = settingsRepository.currency.code,
            value = JavaBigDecimal(total.toStringExpanded()),
        )
    }

    private suspend fun <T> safeCall(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
