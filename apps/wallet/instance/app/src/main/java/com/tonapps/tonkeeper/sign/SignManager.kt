package com.tonapps.tonkeeper.sign

import androidx.core.os.CancellationSignal
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.extensions.toastLoading
import com.tonapps.tonkeeper.ui.screen.action.ActionScreen
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.suspendCancellableCoroutine
import uikit.navigation.Navigation
import java.lang.IllegalArgumentException
import kotlin.coroutines.resume

class SignManager(
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val api: API,
    private val historyHelper: HistoryHelper
) {

    suspend fun action(
        navigation: Navigation,
        wallet: WalletEntity,
        request: SignRequestEntity,
        canceller: CancellationSignal = CancellationSignal(),
    ): String {
        navigation.toastLoading(true)
        val details = emulate(request, wallet)
        navigation.toastLoading(false)

        if (details == null) {
            navigation.toast(Localization.error)
            throw IllegalArgumentException("Failed to emulate")
        }

        val boc = getBoc(navigation, wallet, request, details, canceller) ?: throw IllegalArgumentException("Failed boc")
        api.sendToBlockchain(boc, false)

        return boc
    }

    private suspend fun getBoc(
        navigation: Navigation,
        wallet: WalletEntity,
        request: SignRequestEntity,
        details: HistoryHelper.Details,
        canceller: CancellationSignal
    ) = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { canceller.cancel() }

        val requestKey = "sign_request"
        navigation.setFragmentResultListener(requestKey) { bundle ->
            if (continuation.isActive) {
                continuation.resume(ActionScreen.parseResult(bundle))
            }
        }
        navigation.add(ActionScreen.newInstance(details, wallet.id, request, requestKey))
    }

    private suspend fun emulate(
        request: SignRequestEntity,
        wallet: WalletEntity,
        currency: WalletCurrency = settingsRepository.currency,
    ): HistoryHelper.Details? {
        val rates = ratesRepository.getRates(currency, "TON")
        val seqno = accountRepository.getSeqno(wallet)
        val cell = accountRepository.createSignedMessage(wallet, seqno, EmptyPrivateKeyEd25519, request.validUntil, request.transfers)
        return try {
            val emulated = api.emulate(cell, wallet.testnet)
            historyHelper.create(wallet, emulated, rates)
        } catch (e: Throwable) {
            null
        }
    }
}