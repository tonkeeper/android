package com.tonapps.tonkeeper.ui.component.coin.format

import android.text.InputFilter
import android.text.Spanned

class CoinFormattingFilter(
    private val config: CoinFormattingConfig
): InputFilter {

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int
    ): CharSequence? {
        val isFirst = dstart == 0 && dend == 0
        if (isFirst && (source == CoinFormattingConfig.ZERO || source == config.separator || config.isUnsupportedSeparator(source))) {
            return config.zeroNanoPrefix
        } else if (config.isUnsupportedSeparator(source)) {
            return config.separator
        }
        return null
    }
}