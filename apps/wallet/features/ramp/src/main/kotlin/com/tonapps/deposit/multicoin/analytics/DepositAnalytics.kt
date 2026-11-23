package com.tonapps.deposit.multicoin.analytics

import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowAddFundsOption
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowDepositScreen
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFailureReason
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowNetwork
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowOutcome
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowProviderMode
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowWalletMode
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.deposit.screens.ramp.RampType

// The multicoin ramp screens are shared with the offramp ("sell to card") direction, which reports
// through WithdrawFlow instead — every call here is a no-op unless the flow is an onramp.
class DepositAnalytics(
    private val rampType: RampType,
    private val from: DepositFlowFrom,
    private val walletMode: DepositFlowWalletMode,
    private val utm: UtmParameters = UtmParameters.Empty,
    private val screen: DepositFlowDepositScreen,
) : RampAnalytics {

    private val events: Events.DepositFlow?
        get() {
            if (rampType != RampType.RampOn) {
                return null
            }
            return AnalyticsHelper.Default.eventsTaggedWith(utm).depositFlow
        }

    fun started(availableOptions: List<DepositFlowAddFundsOption>) {
        events?.depositStarted(
            from = from,
            walletMode = walletMode,
            depositScreen = screen,
            availableOptions = availableOptions.toAnalyticsOptions { it.key },
        )
    }

    fun optionClick(addFundsOption: DepositFlowAddFundsOption) {
        events?.depositOptionClick(from = from, walletMode = walletMode, addFundsOption = addFundsOption)
    }

    fun viewReceiveTokens(network: DepositFlowNetwork) {
        events?.depositViewReceiveTokens(
            from = from,
            walletMode = walletMode,
            addFundsOption = DepositFlowAddFundsOption.ReceiveTokens,
            depositScreen = screen,
            network = network,
        )
    }

    override fun viewFiatChooseAsset(availableOptions: List<String>) {
        events?.depositViewFiatChooseAsset(
            from = from,
            addFundsOption = DepositFlowAddFundsOption.BuyWithFiat,
            depositScreen = screen,
            availableOptions = availableOptions.toAnalyticsOptions(),
        )
    }

    override fun clickFiatAsset(cryptoAsset: String) {
        events?.depositClickFiatAsset(
            from = from,
            addFundsOption = DepositFlowAddFundsOption.BuyWithFiat,
            depositScreen = screen,
            buyAsset = cryptoAsset,
        )
    }

    override fun viewFiatPaymentMethod(
        cryptoAsset: String,
        fiatCode: String,
        availableOptions: List<String>,
    ) {
        events?.depositViewFiatPaymentMethod(
            from = from,
            addFundsOption = DepositFlowAddFundsOption.BuyWithFiat,
            depositScreen = screen,
            buyAsset = cryptoAsset,
            sellAsset = fiatCode,
            availableOptions = availableOptions.toAnalyticsOptions(),
        )
    }

    override fun clickFiatPaymentMethod(
        cryptoAsset: String,
        fiatCode: String,
        paymentMethod: String,
    ) {
        events?.depositClickFiatPaymentMethod(
            from = from,
            addFundsOption = DepositFlowAddFundsOption.BuyWithFiat,
            depositScreen = screen,
            buyAsset = cryptoAsset,
            sellAsset = fiatCode,
            paymentMethod = paymentMethod,
        )
    }

    // [addFundsOption] is the option the user came through: P2P is a payment method of `buy_with_fiat`, as on iOS.
    fun continueToP2pMarket(addFundsOption: DepositFlowAddFundsOption) {
        events?.depositContinueToP2pMarket(
            from = from,
            walletMode = walletMode,
            addFundsOption = addFundsOption,
            depositScreen = screen,
        )
    }

    override fun viewRampInsertAmount(
        cryptoAsset: String,
        fiatCode: String,
        paymentMethod: String,
        providerName: String,
    ) {
        viewRampInsertAmount(cryptoAsset, fiatCode, paymentMethod, providerName, providerMode = null)
    }

    fun viewRampInsertAmount(
        cryptoAsset: String,
        fiatCode: String,
        paymentMethod: String,
        providerName: String,
        providerMode: DepositFlowProviderMode?,
    ) {
        events?.depositViewRampInsertAmount(
            from = from,
            addFundsOption = DepositFlowAddFundsOption.BuyWithFiat,
            depositScreen = screen,
            buyAsset = cryptoAsset,
            sellAsset = fiatCode,
            paymentMethod = paymentMethod,
            providerName = providerName,
            providerMode = providerMode,
        )
    }

    // The deposit events carry no amount: it comes from the onramp API by `tx_id`.
    override fun clickRampInsertAmountContinue(
        cryptoAsset: String,
        fiatCode: String,
        paymentMethod: String,
        providerName: String,
        amount: Double,
    ) {
        clickRampInsertAmountContinue(cryptoAsset, fiatCode, paymentMethod, providerName, providerMode = null)
    }

    fun clickRampInsertAmountContinue(
        cryptoAsset: String,
        fiatCode: String,
        paymentMethod: String,
        providerName: String,
        providerMode: DepositFlowProviderMode?,
    ) {
        events?.depositClickRampInsertAmountContinue(
            from = from,
            addFundsOption = DepositFlowAddFundsOption.BuyWithFiat,
            depositScreen = screen,
            buyAsset = cryptoAsset,
            sellAsset = fiatCode,
            paymentMethod = paymentMethod,
            providerName = providerName,
            providerMode = providerMode,
        )
    }

    override fun continueToRampProvider(
        cryptoAsset: String,
        fiatCode: String,
        paymentMethod: String,
        providerName: String,
        amount: Double,
        txId: String,
    ) {
        continueToRampProvider(cryptoAsset, fiatCode, paymentMethod, providerName, providerMode = null, txId)
    }

    fun continueToRampProvider(
        cryptoAsset: String,
        fiatCode: String,
        paymentMethod: String,
        providerName: String,
        providerMode: DepositFlowProviderMode?,
        txId: String,
    ) {
        events?.depositContinueToRampProvider(
            from = from,
            walletMode = walletMode,
            addFundsOption = DepositFlowAddFundsOption.BuyWithFiat,
            depositScreen = screen,
            buyAsset = cryptoAsset,
            sellAsset = fiatCode,
            paymentMethod = paymentMethod,
            providerName = providerName,
            providerMode = providerMode,
            txId = txId,
        )
    }

    fun rampResult(
        cryptoAsset: String,
        fiatCode: String,
        paymentMethod: String,
        providerName: String,
        providerMode: DepositFlowProviderMode,
        txId: String,
        outcome: DepositFlowOutcome,
        failureReason: DepositFlowFailureReason?,
    ) {
        events?.depositRampResult(
            from = from,
            walletMode = walletMode,
            addFundsOption = DepositFlowAddFundsOption.BuyWithFiat,
            depositScreen = screen,
            buyAsset = cryptoAsset,
            sellAsset = fiatCode,
            paymentMethod = paymentMethod,
            providerName = providerName,
            providerMode = providerMode,
            txId = txId,
            outcome = outcome,
            failureReason = failureReason,
        )
    }

    companion object {

        // The schema only enumerates the two networks the legacy TON-only flow could receive on;
        // accounts on any other chain have no reportable value.
        fun networkOrNull(networkId: String): DepositFlowNetwork? {
            return when (networkId) {
                Network.Type.Ton.id -> DepositFlowNetwork.TON
                Network.Type.Tron.id -> DepositFlowNetwork.TRC20
                else -> null
            }
        }
    }
}
