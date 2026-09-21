package com.tonapps.deposit.multicoin.analytics

import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowWithdrawOption
import com.tonapps.deposit.screens.ramp.RampType

// TODO apply for all events
interface RampAnalytics {

    fun viewFiatChooseAsset(availableOptions: List<String>)

    fun clickFiatAsset(cryptoAsset: String)

    fun viewFiatPaymentMethod(
        cryptoAsset: String,
        fiatCode: String,
        availableOptions: List<String>,
    )

    fun clickFiatPaymentMethod(cryptoAsset: String, fiatCode: String, paymentMethod: String)

    fun viewRampInsertAmount(
        cryptoAsset: String,
        fiatCode: String,
        paymentMethod: String,
        providerName: String,
    )

    fun clickRampInsertAmountContinue(
        cryptoAsset: String,
        fiatCode: String,
        paymentMethod: String,
        providerName: String,
        amount: Double,
    )

    fun viewRampAlert(
        cryptoAsset: String,
        fiatCode: String,
        paymentMethod: String,
        providerName: String,
        amount: Double,
    )

    fun continueToRampProvider(
        cryptoAsset: String,
        fiatCode: String,
        paymentMethod: String,
        providerName: String,
        amount: Double,
        txId: String,
    )

    companion object {

        fun of(rampType: RampType, from: DepositFlowFrom): RampAnalytics {
            if (rampType == RampType.RampOn) {
                return DepositAnalytics(rampType, from)
            }
            return WithdrawAnalytics(
                from = WithdrawAnalytics.fromKeyOrDefault(from.key),
                withdrawOption = WithdrawFlowWithdrawOption.SellToCard,
            )
        }
    }
}
