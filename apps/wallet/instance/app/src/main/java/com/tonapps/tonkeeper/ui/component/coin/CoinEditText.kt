package com.tonapps.tonkeeper.ui.component.coin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.R
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.doAfterTextChanged
import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.ui.component.coin.drawable.SuffixDrawable
import com.tonapps.tonkeeper.ui.component.coin.format.CoinFormattingConfig
import com.tonapps.tonkeeper.ui.component.coin.format.CoinFormattingFilter
import com.tonapps.tonkeeper.ui.component.coin.format.CoinFormattingTextWatcher
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.uikit.icon.UIKitIcon
import uikit.base.BaseDrawable
import uikit.extensions.dp
import uikit.extensions.replaceAll
import uikit.extensions.setLeftDrawable
import uikit.extensions.setRightDrawable
import uikit.widget.input.InputTextView

class CoinEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = R.attr.editTextStyle,
) : InputTextView(context, attrs, defStyle) {

    private val suffixDrawable = SuffixDrawable(context)

    var doOnValueChange: ((Double) -> Unit)? = null

    var suffix: String?
        get() = suffixDrawable.text
        set(value) {
            suffixDrawable.text = value
            invalidate()
        }

    init {
        setMaxLength(24)
        // setRightDrawable(suffixDrawable)
        val formattingConfig = CoinFormattingConfig(decimals = 9)
        setFormattingTextWatcher(CoinFormattingTextWatcher(formattingConfig))
        setFormattingInputFilter(CoinFormattingFilter(formattingConfig))
        doAfterTextChanged {
            val value = getValue()
            doOnValueChange?.invoke(value)
        }
    }

    fun getValue(): Double {
        val text = text.toString()
        if (text.isEmpty()) {
            return 0.0
        }
        return Coins.safeParseDouble(text)
    }

    fun setValue(value: Double) {
        val editable = getText() ?: return
        if (0 >= value) {
            clear()
        } else {
            editable.replaceAll(value.toString().removeSuffix(".0"))
        }
    }

    fun clear() {
        text?.clear()
    }

    /*override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = measuredWidth + suffixDrawable.intrinsicWidth
        setMeasuredDimension(width, measuredHeight)
    }*/
}