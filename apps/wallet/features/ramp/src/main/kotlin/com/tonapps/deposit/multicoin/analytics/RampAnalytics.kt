package com.tonapps.deposit.multicoin.analytics

import com.tonapps.blockchain.model.ConfirmInitiator
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowWalletMode
import com.tonapps.bus.generated.Events.SendNative.SendNativeFrom
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowFrom
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowWithdrawOption
import com.tonapps.deposit.screens.ramp.RampType

internal fun <T> Iterable<T>.toAnalyticsOptions(transform: ((T) -> CharSequence)? = null): String {
    return map { (transform?.invoke(it) ?: it).toString() }
        .sorted()
        .joinToString(separator = ",")
}

fun depositFlowFromOrNull(key: String?): DepositFlowFrom? {
    return DepositFlowFrom.entries.firstOrNull { it.key == key }
}

fun withdrawFlowFromOrNull(key: String?): WithdrawFlowFrom? {
    return WithdrawFlowFrom.entries.firstOrNull { it.key == key }
}

fun WithdrawFlowFrom.asDepositFlowFrom(): DepositFlowFrom {
    return depositFlowFromOrNull(key) ?: DepositFlowFrom.Other
}

fun DepositFlowFrom.asWithdrawFlowFrom(): WithdrawFlowFrom {
    return withdrawFlowFromOrNull(key) ?: WithdrawFlowFrom.Other
}

fun SendNativeFrom.asDepositFlowFrom(): DepositFlowFrom {
    return when (this) {
        SendNativeFrom.WalletScreen -> DepositFlowFrom.WalletScreen
        SendNativeFrom.JettonScreen -> DepositFlowFrom.JettonScreen
        SendNativeFrom.DeepLink -> DepositFlowFrom.DeepLink
        SendNativeFrom.QrCode -> DepositFlowFrom.QrCode
        SendNativeFrom.Walletconnect -> DepositFlowFrom.Walletconnect
        SendNativeFrom.TonconnectLocal, SendNativeFrom.TonconnectRemote -> DepositFlowFrom.Tonconnect
    }
}

fun ConfirmInitiator.asDepositFlowFrom(): DepositFlowFrom {
    return when (this) {
        ConfirmInitiator.User -> DepositFlowFrom.WalletScreen
        ConfirmInitiator.DeepLink -> DepositFlowFrom.DeepLink
        ConfirmInitiator.QrCode -> DepositFlowFrom.QrCode
    }
}

fun WithdrawFlowFrom.asSendNativeFrom(): SendNativeFrom {
    return SendNativeFrom.entries.firstOrNull { it.key == key } ?: SendNativeFrom.WalletScreen
}

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

    fun continueToRampProvider(
        cryptoAsset: String,
        fiatCode: String,
        paymentMethod: String,
        providerName: String,
        amount: Double,
        txId: String,
    )

    companion object {

        fun of(
            rampType: RampType,
            from: DepositFlowFrom,
            walletMode: DepositFlowWalletMode,
            utm: UtmParameters = UtmParameters.Empty,
        ): RampAnalytics {
            if (rampType == RampType.RampOn) {
                return DepositAnalytics(rampType, from, walletMode, utm)
            }
            return WithdrawAnalytics(
                from = from.asWithdrawFlowFrom(),
                withdrawOption = WithdrawFlowWithdrawOption.SellToCard,
                utm = utm,
            )
        }
    }
}
