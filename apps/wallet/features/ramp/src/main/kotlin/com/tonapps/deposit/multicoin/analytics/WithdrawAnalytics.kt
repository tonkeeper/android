package com.tonapps.deposit.multicoin.analytics

import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowFeeAsset
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowFrom
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowWithdrawOption

// The multicoin withdraw router only serves the send-tokens option; the sell-to-card option runs
// through DepositMulticoinRouter with RampType.RampOff and reports its own ramp events.
class WithdrawAnalytics(
    private val from: WithdrawFlowFrom,
    private val withdrawOption: WithdrawFlowWithdrawOption = WithdrawFlowWithdrawOption.SendTokens,
) : RampAnalytics {

    private val events: Events.WithdrawFlow
        get() = AnalyticsHelper.Default.events.withdrawFlow

    fun started(availableOptions: List<WithdrawFlowWithdrawOption>) {
        events.withdrawStarted(
            from = from,
            availableOptions = availableOptions.joinToString(separator = ",") { it.key },
        )
    }

    fun optionClick() {
        events.withdrawOptionClick(from = from, withdrawOption = withdrawOption)
    }

    fun viewChooseAsset(availableOptions: List<String>) {
        events.withdrawViewChooseAsset(
            from = from,
            withdrawOption = withdrawOption,
            availableOptions = availableOptions.joinToString(separator = ","),
        )
    }

    fun clickAsset(sellAsset: String) {
        events.withdrawClickAsset(
            from = from,
            withdrawOption = withdrawOption,
            sellAsset = sellAsset,
        )
    }

    override fun viewFiatChooseAsset(availableOptions: List<String>) {
        events.withdrawViewFiatChooseAsset(
            from = from,
            withdrawOption = withdrawOption,
            availableOptions = availableOptions.joinToString(separator = ","),
        )
    }

    override fun clickFiatAsset(cryptoAsset: String) {
        events.withdrawClickFiatAsset(
            from = from,
            withdrawOption = withdrawOption,
            sellAsset = cryptoAsset,
        )
    }

    override fun viewFiatPaymentMethod(
        cryptoAsset: String,
        fiatCode: String,
        availableOptions: List<String>,
    ) {
        events.withdrawViewFiatPaymentMethod(
            from = from,
            withdrawOption = withdrawOption,
            sellAsset = cryptoAsset,
            buyAsset = fiatCode,
            availableOptions = availableOptions.joinToString(separator = ","),
        )
    }

    override fun clickFiatPaymentMethod(
        cryptoAsset: String,
        fiatCode: String,
        paymentMethod: String,
    ) {
        events.withdrawClickFiatPaymentMethod(
            from = from,
            withdrawOption = withdrawOption,
            sellAsset = cryptoAsset,
            buyAsset = fiatCode,
            paymentMethod = paymentMethod,
        )
    }

    override fun viewRampInsertAmount(
        cryptoAsset: String,
        fiatCode: String,
        paymentMethod: String,
        providerName: String,
    ) {
        events.withdrawViewRampInsertAmount(
            from = from,
            withdrawOption = withdrawOption,
            sellAsset = cryptoAsset,
            buyAsset = fiatCode,
            paymentMethod = paymentMethod,
            providerName = providerName,
        )
    }

    override fun clickRampInsertAmountContinue(
        cryptoAsset: String,
        fiatCode: String,
        paymentMethod: String,
        providerName: String,
        amount: Double,
    ) {
        events.withdrawClickRampInsertAmountContinue(
            from = from,
            withdrawOption = withdrawOption,
            sellAsset = cryptoAsset,
            buyAsset = fiatCode,
            paymentMethod = paymentMethod,
            providerName = providerName,
            amount = amount,
        )
    }

    override fun viewRampAlert(
        cryptoAsset: String,
        fiatCode: String,
        paymentMethod: String,
        providerName: String,
        amount: Double,
    ) {
        events.withdrawViewRampAlert(
            from = from,
            withdrawOption = withdrawOption,
            sellAsset = cryptoAsset,
            buyAsset = fiatCode,
            paymentMethod = paymentMethod,
            providerName = providerName,
            amount = amount,
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
        events.withdrawContinueToRampProvider(
            from = from,
            withdrawOption = withdrawOption,
            sellAsset = cryptoAsset,
            buyAsset = fiatCode,
            paymentMethod = paymentMethod,
            providerName = providerName,
            amount = amount,
            txId = txId,
        )
    }

    fun viewInsertAmount(sellAsset: String, symbol: String) {
        events.withdrawViewInsertAmount(
            from = from,
            withdrawOption = withdrawOption,
            sellAsset = sellAsset,
            stablecoinSymbol = symbol,
            buyAsset = sellAsset,
        )
    }

    fun clickInsertAmountContinue(sellAsset: String, symbol: String, amount: Double) {
        events.withdrawClickInsertAmountContinue(
            from = from,
            withdrawOption = withdrawOption,
            sellAsset = sellAsset,
            stablecoinSymbol = symbol,
            buyAsset = sellAsset,
            amount = amount,
        )
    }

    fun sendConfirm(sellAsset: String, symbol: String, amount: Double) {
        events.withdrawSendConfirm(
            from = from,
            withdrawOption = withdrawOption,
            sellAsset = sellAsset,
            stablecoinSymbol = symbol,
            buyAsset = sellAsset,
            amount = amount,
            feeAsset = WithdrawFlowFeeAsset.Coin,
        )
    }

    fun sendSuccess(sellAsset: String, symbol: String, amount: Double) {
        events.withdrawSendSuccess(
            from = from,
            withdrawOption = withdrawOption,
            sellAsset = sellAsset,
            stablecoinSymbol = symbol,
            buyAsset = sellAsset,
            amount = amount,
            feeAsset = WithdrawFlowFeeAsset.Coin,
        )
    }

    companion object {

        fun fromKeyOrDefault(key: String?): WithdrawFlowFrom {
            return WithdrawFlowFrom.entries.firstOrNull { it.key == key }
                ?: WithdrawFlowFrom.WalletScreen
        }
    }
}
