package com.tonapps.tonkeeper.ui.component.coin.format

import android.text.InputFilter
import android.text.Spanned
import com.tonapps.core.components.normalizeAmountInput

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
        val inserted = source.subSequence(start, end).toString()
        val normalized = inserted.normalizeAmountInput()
        val isSeparator = normalized == config.separator || config.isUnsupportedSeparator(normalized)
        val isFirst = dstart == 0 && dend == 0
        if (isFirst && (normalized == CoinFormattingConfig.ZERO || isSeparator)) {
            return config.zeroNanoPrefix
        } else if (config.isUnsupportedSeparator(normalized)) {
            return config.separator
        }
        return normalized.takeIf { it != inserted }
    }
}
