package com.tonapps.tonkeeper.ui.component.coin

import android.content.Context
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.R
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.ui.component.coin.drawable.SuffixDrawable
import com.tonapps.tonkeeper.ui.component.coin.format.CoinFormattingConfig
import com.tonapps.tonkeeper.ui.component.coin.format.CoinFormattingFilter
import com.tonapps.tonkeeper.ui.component.coin.format.CoinFormattingTextWatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

    private data class SizeState(
        val safeAvailableWidth: Float = 0f,
        val textWidth: Float = 0f,
        val currentTextSize: Float = 0f
    )

    private val suffixDrawable = SuffixDrawable(context)
    private val _sizeStateFlow = MutableStateFlow(SizeState())
    @OptIn(FlowPreview::class)
    private val sizeStateFlow = _sizeStateFlow.asStateFlow()

    @OptIn(FlowPreview::class)
    private val textSizeFlow = sizeStateFlow.map { state ->
        findBestTextSize(state.safeAvailableWidth)
    }.distinctUntilChanged()

    private lateinit var formattingConfig: CoinFormattingConfig
    private val stepValue = 2f.sp
    private val minTextSize = 12f.sp
    private val initTextSize: Float by lazy { textSize }
    private var maxWidthConstraint: Int = 0
    private var initMeasuredHeight: Int = 0
    private var notifyUpdateRunnable: Runnable? = null

    private val sizeStepsPx: List<Float> by lazy {
        val steps = mutableSetOf<Float>()
        var currentSize = initTextSize
        while (currentSize >= minTextSize) {
            steps.add(currentSize)
            currentSize -= stepValue
        }
        steps.add(minTextSize)
        steps.sortedDescending()
    }

    var doOnValueChange: ((value: Double, byUser: Boolean) -> Unit)? = null
    var doOnTextChange: ((text: String) -> Unit)? = null

    var suffix: String?
        get() = suffixDrawable.text
        set(value) {
            suffixDrawable.text = value
            updatePadding()
            invalidate()
        }

    var valueScale = 0
    var onTextSizeChange: ((unit: Int, textSize: Float) -> Unit)? = null

    private val textWidth: Float
        get() {
            val value = text ?: return 0f
            return paint.measureText(value, 0, value.length)
        }

    private val availableWidth: Float
        get() {
            val containerWidth = maxWidthConstraint - paddingLeft - paddingRight
            val suffixWidth = if (suffix.isNullOrEmpty()) 0f else (suffixDrawable.intrinsicWidth + compoundDrawablePadding).toFloat()
            return (containerWidth - suffixWidth).coerceAtLeast(0f)
        }

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
            doOnTextChange?.invoke(text.toString())
            checkTextSize()
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
    }

    private fun setTextSizePx(newTextSize: Float) {
        if (textSize != newTextSize) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, newTextSize)
            suffixDrawable.textSize = newTextSize
            onTextSizeChange?.invoke(TypedValue.COMPLEX_UNIT_PX, newTextSize)
        }
    }

    private fun applyTextSize(newTextSize: Float) {
        setTextSizePx(newTextSize)
    }

    private fun findBestTextSize(safeAvailableWidth: Float): Float {
        val textValue = text ?: return initTextSize
        val textPaint = TextPaint(paint)
        for (stepSize in sizeStepsPx) {
            textPaint.textSize = stepSize
            if (textPaint.measureText(textValue, 0, textValue.length) <= safeAvailableWidth) {
                return stepSize
            }
        }
        return minTextSize
    }

    private fun checkTextSize() {
        val safeAvailableWidth = availableWidth - 24f.dp
        if (safeAvailableWidth > 0) {
            _sizeStateFlow.value = SizeState(safeAvailableWidth, textWidth, textSize)
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
    }

    fun setValue(
        value: BigDecimal,
        notifyByUser: Boolean = false,
        customValueScale: Int = valueScale
    ) {
        val string = if (customValueScale == 0) value.asString() else value.setScale(customValueScale, RoundingMode.HALF_EVEN).asString()
        if (string.isNullOrBlank() && value != BigDecimal.ZERO) {
            val newCustomValueScale = CurrencyFormatter.getScale(value)
            if (newCustomValueScale != customValueScale) {
                setValue(value, notifyByUser, CurrencyFormatter.getScale(value))
            }
            return
        } else if (string == null) {
            clear()
        } else if (string != text?.toString()) {
            text?.clear()
            text?.insert(0, string)
        }
        if (notifyByUser) {
            notifyUpdateDelay(true)
        }
    }

    fun setValue(
        value: String?,
        notifyByUser: Boolean = false,
    ) {
        if (value.isNullOrBlank()) {
            clear()
        } else if (value != text?.toString()) {
            text?.clear()
            text?.insert(0, value)
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
        val specMode = MeasureSpec.getMode(widthMeasureSpec)
        val specSize = MeasureSpec.getSize(widthMeasureSpec)

        if (specMode == MeasureSpec.EXACTLY || specMode == MeasureSpec.AT_MOST) {
            if (specSize > 0) {
                maxWidthConstraint = specSize
                checkTextSize()
            }
        }

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
        if (initMeasuredHeight == 0 && h > 0) {
            initMeasuredHeight = h
        }

        if (w != oldw) {
            checkTextSize()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val lifecycleOwner = findViewTreeLifecycleOwner()
        lifecycleOwner?.lifecycleScope?.launch {
            lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                textSizeFlow.collect(::applyTextSize)
            }
        }
    }

    companion object {

        fun BigDecimal.asString(): String? {
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

        fun BigDecimal.asString2(
            customValueScale: Int = 0,
        ): String {
            val string = if (customValueScale == 0) asString() else setScale(customValueScale, RoundingMode.HALF_EVEN).asString()
            if (string.isNullOrBlank() && this != BigDecimal.ZERO) {
                val newCustomValueScale = CurrencyFormatter.getScale(this)
                if (newCustomValueScale != customValueScale) {
                    return asString2(CurrencyFormatter.getScale(this))
                }
            }
            return string ?: ""
        }

    }
}