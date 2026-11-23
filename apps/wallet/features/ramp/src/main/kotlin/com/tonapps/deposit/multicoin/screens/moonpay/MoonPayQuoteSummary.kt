package com.tonapps.deposit.multicoin.screens.moonpay

import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuote
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayMoney
import com.tonapps.deposit.multicoin.screens.buy.displayAmount
import com.tonapps.deposit.multicoin.screens.buy.tightFiat
import com.tonapps.deposit.multicoin.screens.buy.walletGrouping
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import java.math.BigDecimal
import java.math.RoundingMode

// What a MoonPay quote is made of, as the payment status sheet and the Buy screen's price details show it.
internal class MoonPayQuoteSummary(private val quote: MoonPayBuyQuote, private val assetLabel: String?) {

    private val fees: Map<String, BigDecimal> = paidFees()

    val summary: MoonPayPaymentSummary
        get() = MoonPayPaymentSummary(
            paid = formatPaidShort(quote.source),
            receive = formatReceived(quote.destination),
            feesTotal = formatPaid(fees.values.sum()),
            fees = feeRows(),
        )

    // Every digit MoonPay sent, so the rows add up to the total and a large amount keeps its cents.
    fun formatPaid(amount: BigDecimal): String {
        return walletGrouping(tightFiat(CurrencyFormatter.formatFiatExact(quote.source.asset.code.orEmpty().uppercase(), amount)))
    }

    // The rows and the total take the same fees: without the deprecated `partner`, which repeats
    // `ecosystem`, and only those in the currency paid.
    private fun paidFees(): Map<String, BigDecimal> {
        val source = quote.source.asset.code
        return quote.fees.orEmpty()
            .filter { (key, money) ->
                !key.equals(PARTNER_FEE, ignoreCase = true) && money.asset.code.equals(source, ignoreCase = true)
            }
            .mapNotNull { (key, money) -> money.amount?.toBigDecimalOrNull()?.let { key.lowercase() to it } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, amounts) -> amounts.sum() }
    }

    // Every kind without a row of its own, such as `defi`, goes to Other, so the rows add up to the total.
    private fun feeRows(): List<MoonPayPaymentFee> {
        val named = NAMED_FEES.mapNotNull { (key, kind) -> fees[key]?.let { MoonPayPaymentFee(kind, formatPaid(it)) } }
        val others = fees.filterKeys { it !in NAMED_FEES }.values
        return if (others.isEmpty()) {
            named
        } else {
            named + MoonPayPaymentFee(MoonPayPaymentFeeKind.Other, formatPaid(others.sum()))
        }
    }

    private fun Collection<BigDecimal>.sum(): BigDecimal = fold(BigDecimal.ZERO, BigDecimal::add)

    private fun formatPaidShort(money: MoonPayMoney): String {
        val amount = money.amount?.toBigDecimalOrNull() ?: return NO_VALUE
        return if (amount.stripTrailingZeros().scale() <= 0) {
            val code = money.asset.code.orEmpty().uppercase()
            walletGrouping(tightFiat(CurrencyFormatter.formatFull(code, Coins.of(amount.setScale(0, RoundingMode.DOWN), 0), 0)))
        } else {
            formatPaid(amount)
        }
    }

    private fun formatReceived(money: MoonPayMoney): String {
        val amount = money.amount?.toBigDecimalOrNull() ?: return NO_VALUE
        val label = assetLabel ?: money.asset.code.orEmpty().uppercase()
        val rounded = displayAmount(amount)
        val scale = rounded.scale().coerceAtLeast(0)
        return "${walletGrouping(CurrencyFormatter.formatFull("", Coins.of(rounded, scale), scale))} $label"
    }

    companion object {
        const val NO_VALUE = "—"
        private const val PARTNER_FEE = "partner"
        private val NAMED_FEES = mapOf(
            "moonpay" to MoonPayPaymentFeeKind.MoonPay,
            "ecosystem" to MoonPayPaymentFeeKind.Ecosystem,
            "network" to MoonPayPaymentFeeKind.Network,
        )
    }
}
