package com.tonapps.tonkeeper.ui.screen.swap.omniston.state

import android.content.Context
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.InsufficientFundsException
import com.tonapps.tonkeeper.extensions.formattedAmount
import com.tonapps.tonkeeper.extensions.formattedCharges
import com.tonapps.tonkeeper.extensions.method
import com.tonapps.tonkeeper.ui.screen.onramp.main.view.CurrencyInputView
import com.tonapps.tonkeeper.ui.screen.send.main.state.SendFee
import com.tonapps.tonkeeper.usecase.emulation.Emulated
import com.tonapps.wallet.data.account.entities.MessageBodyEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.settings.entities.PreferredFeeMethod

data class SwapQuoteState(
    val toUnits: Coins = Coins.ZERO,
    val fromUnits: Coins = Coins.ZERO,
    val fromCurrency: WalletCurrency = WalletCurrency.USDT_TON,
    val toCurrency: WalletCurrency = WalletCurrency.TON,
    val provider: String = "",
    val blockchainFee: Coins = Coins.ZERO,
    val signRequest: SignRequestEntity? = null,
    val confirm: Boolean = false,
    val gasBudget: Coins = Coins.ZERO,
    val estimatedGasConsumption: Coins = Coins.ZERO,
    val tx: Tx? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val selectedFee: SendFee? = null,
    val insufficientFunds: InsufficientFundsException? = null,
    val canEditFeeMethod: Boolean = true,
    val slippage: Int = 0
) {

    data class Tx(
        val sendTonFee: SendFee? = null,
        val tonEmulated: Emulated? = null,
        val sendBatteryFee: SendFee? = null,
        val batteryEmulated: Emulated? = null,
        val messageBody: MessageBodyEntity? = null,
    ) {

        fun getFeeByMethod(method: PreferredFeeMethod): SendFee? {
            if (method == PreferredFeeMethod.BATTERY && sendBatteryFee != null) {
                return sendBatteryFee
            }
            return sendTonFee
        }
    }

    val isEmpty: Boolean
        get() = toUnits.isZero

    val canUseBattery: Boolean
        get() = tx?.sendBatteryFee != null && tx.batteryEmulated != null

    val feeOptions: List<SendFee>
        get() = listOfNotNull(
            tx?.sendBatteryFee,
            tx?.sendTonFee,
        )

    val totalFee: Coins by lazy {
        val emulatedFee = tx?.tonEmulated?.totalFees ?: Coins.ZERO
        listOf(gasBudget, estimatedGasConsumption, emulatedFee).max()
    }

    val isPreferredFeeMethodBattery: Boolean
        get() = selectedFee?.method == PreferredFeeMethod.BATTERY

    val toUnitsFormat: CharSequence by lazy {
        CurrencyInputView.EQUALS_SIGN_PREFIX + CurrencyFormatter.format(toCurrency.code, toUnits)
    }

    val fromUnitsFormat: CharSequence by lazy {
        CurrencyFormatter.format(fromCurrency.code, fromUnits)
    }

    val exchangeRate: CharSequence by lazy {
        if (toUnits.isZero || fromUnits.isZero) {
            return@lazy ""
        }

        val rate = toUnits / fromUnits
        val value = Coins.ONE
        val fromFormat = CurrencyFormatter.format(fromCurrency.code, value)
        val toFormat = CurrencyFormatter.format(toCurrency.code, rate * value)

        "$fromFormat ≈ $toFormat"
    }

    fun getFeeFormat(context: Context): CharSequence {
        val format = when (selectedFee) {
            is SendFee.Battery -> return selectedFee.formattedCharges(context)
            else -> CurrencyFormatter.format("TON", totalFee)
        }
        return CurrencyInputView.EQUALS_SIGN_PREFIX + format
    }
}
