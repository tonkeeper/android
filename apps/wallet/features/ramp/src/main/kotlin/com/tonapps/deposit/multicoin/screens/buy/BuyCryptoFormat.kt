package com.tonapps.deposit.multicoin.screens.buy

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

// As iOS's formatDisplayAmount (TK-5948), half even like its number formatter.
internal fun displayAmount(amount: BigDecimal): BigDecimal {
    val rounded = if (amount.abs() >= BigDecimal.ONE) {
        amount.setScale(2, RoundingMode.HALF_EVEN)
    } else {
        amount.round(MathContext(SIGNIFICANT_BELOW_ONE, RoundingMode.HALF_EVEN))
    }
    val stripped = rounded.stripTrailingZeros()
    return when {
        stripped.scale() < 0 -> stripped.setScale(0)
        else -> stripped
    }
}

// A price in fiat as iOS's formatDisplayAmount writes it: «$2 492.72», «$0.0492».
internal fun formatFiatPrice(value: BigDecimal, currency: String): String {
    val rounded = displayAmount(value)
    return CurrencyFormatter.formatFull(currency, Coins.of(rounded), rounded.scale().coerceAtLeast(0)).toString()
}

internal fun walletGrouping(formatted: CharSequence): String {
    return regroup(
        formatted.toString(),
        from = CurrencyFormatter.groupingSeparator,
        to = walletGroupingSeparator,
    )
}

internal val walletGroupingSeparator: String
    get() = Formatter.defaultOptions.groupingSeparator.orEmpty()

internal fun tightFiat(formatted: CharSequence): String {
    return formatted.toString()
        .replace(THIN_SPACE, "")
        .replace(SYMBOL_AFTER_DIGIT, "")
}

// Only between digits: a symbol such as «Bs.» keeps its dot where the locale groups with one.
internal fun regroup(formatted: String, from: String, to: String): String {
    return if (from.isEmpty() || from == to) {
        formatted
    } else {
        formatted.replace(Regex("(?<=\\d)${Regex.escape(from)}(?=\\d)"), Regex.escapeReplacement(to))
    }
}

internal class GroupedAmountTransformation(
    private val separator: String,
    private val color: Color?,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val integerEnd = raw.indexOfFirst { it == '.' || it == ',' }.takeIf { it >= 0 } ?: raw.length
        val shown = StringBuilder()
        val toShown = IntArray(raw.length + 1)
        raw.forEachIndexed { index, char ->
            if (index in 1 until integerEnd && (integerEnd - index) % GROUP_SIZE == 0) {
                shown.append(separator)
            }
            toShown[index] = shown.length
            shown.append(char)
        }
        toShown[raw.length] = shown.length
        val toRaw = IntArray(shown.length + 1)
        var index = 0
        for (offset in 0..shown.length) {
            while (index < raw.length && toShown[index] < offset) {
                index++
            }
            toRaw[offset] = index
        }
        val annotated = if (color != null) {
            AnnotatedString(shown.toString(), listOf(AnnotatedString.Range(SpanStyle(color = color), 0, shown.length)))
        } else {
            AnnotatedString(shown.toString())
        }
        return TransformedText(
            annotated,
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = toShown[offset.coerceIn(0, raw.length)]

                override fun transformedToOriginal(offset: Int): Int = toRaw[offset.coerceIn(0, shown.length)]
            },
        )
    }

    override fun equals(other: Any?): Boolean {
        return other is GroupedAmountTransformation && other.separator == separator && other.color == color
    }

    override fun hashCode(): Int = 31 * separator.hashCode() + color.hashCode()
}

private const val GROUP_SIZE = 3
private const val SIGNIFICANT_BELOW_ONE = 3
private const val THIN_SPACE = " "
private val SYMBOL_AFTER_DIGIT = Regex("(?<=\\d) (?=[^\\p{L}\\d\\s])")