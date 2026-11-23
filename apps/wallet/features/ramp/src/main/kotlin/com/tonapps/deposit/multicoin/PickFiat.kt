package com.tonapps.deposit.multicoin

import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.multicoin.data.RampAssetDetail
import com.tonapps.log.L
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException

// `supported` are the fiats an asset can be bought with, `known` the app's currency list; an
// empty list means it failed to load and every supported fiat counts.
internal fun pickFiat(supported: List<String>, known: List<String>, preferred: String): String {
    val fiats = known.ifEmpty { supported }.filter { code -> supported.any { it.equals(code, ignoreCase = true) } }
    return fiats.firstOrNull { it.equals(preferred, ignoreCase = true) }
        ?: fiats.firstOrNull { it.equals(WalletCurrency.USD.code, ignoreCase = true) }
        ?: fiats.firstOrNull()
        ?: WalletCurrency.USD.code
}

// [code] when it is a fiat, else USD: the app's currency may be a crypto one (TON).
internal fun fiatOrUsd(code: String): String = WalletCurrency.of(code)?.takeIf { it.isFiat }?.code ?: WalletCurrency.USD.code

internal val RampAssetDetail.supportedFiats: List<String>
    get() = methods.flatMap { method -> method.providers.map { it.fiat } }.distinct()

internal suspend fun loadKnownFiats(
    walletId: String,
    accountRepository: UnifiedAccountRepository,
    exchangeRepository: ExchangeRepository,
    settingsRepository: SettingsRepository,
): List<String> {
    return try {
        val wallet = requireNotNull(accountRepository.getTonWalletById(walletId)) { "No wallet $walletId" }
        exchangeRepository
            .getCurrencies(wallet.network, settingsRepository.getLocale(), wallet.multichainWalletId)
            .map { it.code }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        L.e(e)
        emptyList()
    }
}
