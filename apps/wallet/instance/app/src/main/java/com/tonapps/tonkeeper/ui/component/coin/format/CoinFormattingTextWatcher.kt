package com.tonapps.tonkeeper.ui.component.coin.format

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import com.tonapps.icu.CurrencyFormatter
import uikit.extensions.deleteLast
import uikit.extensions.replaceAll
import uikit.extensions.replaceLast

class CoinFormattingTextWatcher(
    private val config: CoinFormattingConfig
): TextWatcher {

    override fun afterTextChanged(editable: Editable) {
        val string = editable.toString()
        val separatorCount = editable.count { it == config.separator[0] }
        if (separatorCount > 1) {
            val separatorIndex = editable.indexOfLast { it == config.separator[0] }
            editable.delete(separatorIndex, separatorIndex + 1)
            return
        } else if (string == CoinFormattingConfig.DOUBLE_ZERO) {
            editable.replaceAll(config.zeroNanoPrefix)
            return
        } else if (string == CoinFormattingConfig.ZERO) {
            editable.clear()
            return
        }
        val index = editable.indexOf(config.separator)
        if (index != -1) {
            val decimalPart = editable.substring(index + 1)
            if (decimalPart.length > config.decimals) {
                editable.delete(index + config.decimals + 1, editable.length)
            }
        }
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) { }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {  }

}