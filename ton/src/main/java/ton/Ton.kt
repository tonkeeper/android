package ton

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

data class Ton(
    val nano: Long
) {

    companion object {
        private const val BASE = 1000000000L
        private val format = NumberFormat.getCurrencyInstance(Locale.US).apply {
            val decimalFormatSymbols = (this as DecimalFormat).decimalFormatSymbols
            decimalFormatSymbols.currencySymbol = ""
            this.decimalFormatSymbols = decimalFormatSymbols
        }

        val ZERO = Ton(0)

    }

    constructor(coins: Float): this((coins * BASE).toLong())

    val coins: Float
        get() = nano / BASE.toFloat()


    fun toUserLike(): String {
        return format.format(coins)
    }

    fun convertTo(amount: Amount): Amount {
        val value = amount.value * coins
        return Amount(amount.currency, value)
    }
}