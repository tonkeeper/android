package com.tonapps.tonkeeper.extensions

import android.widget.EditText
import com.tonapps.icu.CurrencyFormatter
import java.math.BigDecimal

fun EditText.setBigDecimal(value: BigDecimal) {
    text?.clear()
    if (BigDecimal.ZERO != value) {
        val string = value.stripTrailingZeros()
            .toPlainString()
            .removeSuffix(".0")
            .replace(".", CurrencyFormatter.monetaryDecimalSeparator)
        text?.insert(0, string)
    }
}
