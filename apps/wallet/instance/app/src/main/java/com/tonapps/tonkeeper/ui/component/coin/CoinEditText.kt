package com.tonapps.tonkeeper.ui.component.coin

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.appcompat.R
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doAfterTextChanged
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.ui.component.coin.drawable.SuffixDrawable
import com.tonapps.tonkeeper.ui.component.coin.format.CoinFormattingConfig
import com.tonapps.tonkeeper.ui.component.coin.format.CoinFormattingFilter
import com.tonapps.tonkeeper.ui.component.coin.format.CoinFormattingTextWatcher
import uikit.extensions.dp
import uikit.extensions.isVisibleForUser
import uikit.extensions.replaceAll
import uikit.extensions.setRightDrawable
import uikit.extensions.sp
import uikit.widget.input.InputTextView
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

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
    private var notifyUpdateRunnable: Runnable? = null

    var doOnValueChange: ((value: Double, byUser: Boolean) -> Unit)? = null

    var suffix: String?
        get() = suffixDrawable.text
        set(value) {
            suffixDrawable.text = value
            updatePadding()
            invalidate()
        }

    var valueScale = 0

    init {
        setMaxLength(18)
        setRightDrawable(suffixDrawable)
        compoundDrawablePadding = 8.dp
        setDecimals(9)
    }

    val decimals: Int
        get() = formattingConfig.decimals

    override fun onTextChanged(
        text: CharSequence?,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int
    ) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        if (isVisibleForUser) {
            val byUser = abs(lengthAfter - lengthBefore) == 1 && isFocused
            notifyUpdateDelay(byUser)
        }
    }

    private fun notifyUpdateDelay(byUser: Boolean) {
        notifyUpdateRunnable?.let(::removeCallbacks)
        notifyUpdateRunnable = Runnable {
            notifyUpdate(byUser)
            notifyUpdateRunnable = null
        }
        postDelayed(notifyUpdateRunnable, 64)
    }

    private fun notifyUpdate(byUser: Boolean) {
        val value = getValue()
        doOnValueChange?.invoke(value, byUser)
        adjustTextSize()
    }

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

    fun setValue(value: BigDecimal, notifyByUser: Boolean = false) {
        val string = if (valueScale == 0) value.asString() else value.setScale(valueScale, RoundingMode.DOWN).asString()
        if (string == null) {
            clear()
            adjustTextSize()
        } else if (string != text?.toString()) {
            text?.clear()
            text?.insert(0, string)
            adjustTextSize()
        }
        if (notifyByUser) {
            notifyUpdateDelay(true)
        }
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

    private companion object {

        private fun BigDecimal.asString(): String? {
            if (BigDecimal.ZERO == this) {
                return null
            }
            val string = stripTrailingZeros()
                .toPlainString()
                .removeSuffix(".0")
                .replace(".", CurrencyFormatter.monetaryDecimalSeparator)
                .trim()

            if (string.isEmpty() || string == "0") {
                return null
            }
            return string
        }

    }
}