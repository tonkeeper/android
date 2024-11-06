package com.tonapps.tonkeeper.ui.component.coin

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.appcompat.R
import androidx.core.widget.TextViewCompat
import androidx.core.widget.doAfterTextChanged
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.extensions.setBigDecimal
import com.tonapps.tonkeeper.ui.component.coin.drawable.SuffixDrawable
import com.tonapps.tonkeeper.ui.component.coin.format.CoinFormattingConfig
import com.tonapps.tonkeeper.ui.component.coin.format.CoinFormattingFilter
import com.tonapps.tonkeeper.ui.component.coin.format.CoinFormattingTextWatcher
import uikit.extensions.dp
import uikit.extensions.replaceAll
import uikit.extensions.scale
import uikit.extensions.setRightDrawable
import uikit.extensions.sp
import uikit.widget.input.InputTextView
import java.math.BigDecimal
import kotlin.properties.Delegates

class CoinEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = R.attr.editTextStyle,
) : InputTextView(context, attrs, defStyle) {

    private val suffixDrawable = SuffixDrawable(context)

    private lateinit var formattingConfig: CoinFormattingConfig
    private val initTextSize: Float by lazy { textSize }
    private var initMeasuredHeight: Int = 0
    private val availableWidth: Float by lazy {
        val parentView = parent as? View ?: return@lazy 0f
        val parentWidth = parentView.width - parentView.paddingStart - parentView.paddingEnd
        val suffixWidth = suffixDrawable.intrinsicWidth + compoundDrawablePadding
        (parentWidth - suffixWidth) * 0.6f
    }

    private var isAdjustingTextSize = false
    private val minTextSize = 12f.sp

    var doOnValueChange: ((Double) -> Unit)? = null

    var suffix: String?
        get() = suffixDrawable.text
        set(value) {
            suffixDrawable.text = value
            updatePadding()
            invalidate()
        }

    init {
        setMaxLength(18)
        setRightDrawable(suffixDrawable)
        compoundDrawablePadding = 8.dp
        setDecimals(9)
        doAfterTextChanged {
            val value = getValue()
            doOnValueChange?.invoke(value)
            adjustTextSize()
        }
    }

    val decimals: Int
        get() = formattingConfig.decimals

    private fun adjustTextSize() {
        if (isAdjustingTextSize) return
        isAdjustingTextSize = true

        try {
            val textStr = text.toString()
            if (textStr.isEmpty()) return

            val maxTextSize = initTextSize
            val paint = paint
            val availableWidth = this.availableWidth

            var low = minTextSize
            var high = maxTextSize
            var bestSize = low

            while (low <= high) {
                val midSize = (low + high) / 2f
                paint.textSize = midSize
                val textWidth = paint.measureText(textStr)

                if (textWidth <= availableWidth) {
                    bestSize = midSize
                    low = midSize + 0.5f
                } else {
                    high = midSize - 0.5f
                }
            }

            // Only set the text size if it has changed
            if (textSize != bestSize) {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, bestSize)
                suffixDrawable.textSize = bestSize
            }
        } finally {
            isAdjustingTextSize = false
        }
    }

    fun setDecimals(decimals: Int) {
        formattingConfig = CoinFormattingConfig(decimals = decimals)
        setFormattingTextWatcher(CoinFormattingTextWatcher(formattingConfig))
        setFormattingInputFilter(CoinFormattingFilter(formattingConfig))
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
        adjustTextSize()
    }

    fun setValue(value: BigDecimal) {
        setBigDecimal(value)
        adjustTextSize()
    }

    fun clear() {
        text?.clear()
    }

    private fun updatePadding() {
        val suffixWidth = suffixDrawable.intrinsicWidth
        val paddingRight = suffixWidth + compoundDrawablePadding
        setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
        if (0 >= initMeasuredHeight) {
            initMeasuredHeight = measuredHeight
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (initMeasuredHeight > 0) {
            val heightMode = MeasureSpec.getMode(heightMeasureSpec)
            val heightSize = MeasureSpec.getSize(heightMeasureSpec)
            val height = when (heightMode) {
                MeasureSpec.EXACTLY -> heightSize
                MeasureSpec.AT_MOST -> heightSize.coerceAtMost(initMeasuredHeight)
                else -> initMeasuredHeight
            }
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
        updatePadding()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        adjustTextSize()
    }
}