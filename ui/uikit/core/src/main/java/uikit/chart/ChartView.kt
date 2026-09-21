package uikit.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.uikit.color.textTertiaryColor
import uikit.extensions.dp
import uikit.extensions.setTextAppearance

class ChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private enum class State { LOADING, EMPTY, DATA }

    private val chartDrawable = ChartDrawable(context)
    private val touchIndicatorDrawable = TouchIndicatorDrawable(context)
    private val loadingDrawable = LoadingDrawable(context)
    private val emptyDrawable = EmptyChartDrawable(context)
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private var state: State = State.LOADING

    private val axisPriceEndPadding = 16f.dp
    private val axisPriceMinBottomPadding = 24f.dp
    private val timeAxisLabelLineGap = 6f.dp
    private val timeAxisLabelBottom = 2f.dp
    private val chartAreaTopInset = 20f.dp
    private val chartAreaBottomInset = 45f.dp

    private var selectedPoint: ChartPoint? = null
    private var lastChartPayload: List<ChartPoint>? = null

    var onPointSelected: ((ChartPoint?) -> Unit)? = null

    var emptyText: String
        get() = emptyDrawable.text
        set(value) {
            emptyDrawable.text = value
            if (state == State.EMPTY) invalidate()
        }

    var formatAxisPrice: ((Float) -> String)? = null
        set(value) {
            if (field !== value) {
                field = value
                invalidate()
            }
        }

    var formatAxisTime: ((Long) -> String)? = null
        set(value) {
            if (field !== value) {
                field = value
                invalidate()
            }
        }

    init {
        labelPaint.setTextAppearance(context, uikit.R.style.TextAppearance_Body3)
        labelPaint.typeface = Typeface.MONOSPACE
        background = chartDrawable
        touchIndicatorDrawable.callback = this
        loadingDrawable.callback = this
        emptyDrawable.callback = this
    }

    fun setData(data: List<ChartPoint>?, isSquare: Boolean) {
        if (data == null) {
            if (state == State.LOADING) return

            state = State.LOADING
            lastChartPayload = null
            chartDrawable.setData(emptyList(), isSquare)
            clearSelection(dispatchCallback = true)
            invalidate()
            return
        }

        val payload = data.toChartPayload()
        val newState = if (payload.isEmpty()) State.EMPTY else State.DATA
        if (state == newState && lastChartPayload == payload) return

        state = newState
        lastChartPayload = payload
        chartDrawable.setData(payload, isSquare)
        clearSelection(dispatchCallback = true)
        invalidate()
    }

    override fun verifyDrawable(who: Drawable): Boolean {
        return super.verifyDrawable(who) ||
                who == touchIndicatorDrawable ||
                who == loadingDrawable ||
                who == emptyDrawable
    }

    override fun draw(canvas: Canvas) {
        applyChartAreaBoundsIfNeeded()
        super.draw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        when (state) {
            State.LOADING -> loadingDrawable.draw(canvas)
            State.EMPTY -> emptyDrawable.draw(canvas)
            State.DATA -> {
                drawAxisPriceLabels(canvas)
                drawAxisGuideTimeLabels(canvas)
                touchIndicatorDrawable.draw(canvas)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (state != State.DATA) return super.onTouchEvent(event)
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateTouch(event.x - paddingLeft)
                true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                clearSelection(dispatchCallback = true)
                parent?.requestDisallowInterceptTouchEvent(false)
                true
            }

            else -> super.onTouchEvent(event)
        }
    }

    private fun applyChartAreaBoundsIfNeeded() {
        val top = chartAreaTopInset.toInt().coerceAtMost(maxOf(0, height - 1))
        val rawBottom = height - chartAreaBottomInset.toInt()
        val bottom = rawBottom.coerceIn(top + 1, height)

        chartDrawable.verticalGuidesBottomExtraPx = chartAreaBottomInset

        if (!emptyDrawable.bounds.matches(0, 0, width, height)) {
            emptyDrawable.setBounds(0, 0, width, height)
        }

        if (chartDrawable.bounds.matches(0, top, width, bottom)) return

        chartDrawable.setBounds(0, top, width, bottom)
        // The touch indicator line extends through the bottom time-axis zone.
        touchIndicatorDrawable.setBounds(0, top, width, height)
        loadingDrawable.setBounds(0, top, width, bottom)
    }

    private fun drawAxisGuideTimeLabels(canvas: Canvas) {
        val formatter = formatAxisTime ?: return

        labelPaint.color = context.textTertiaryColor
        val baselineY = timeAxisLabelBaselineY()

        labelPaint.withTextAlign(Paint.Align.LEFT) {
            for (index in TIME_LABEL_GUIDE_INDICES) {
                val timestamp = chartDrawable.verticalGuideTimestamp(index) ?: continue
                val text = formatter(timestamp)
                if (text.isEmpty()) continue

                val x = chartDrawable.verticalGuideAnchorX(index) + timeAxisLabelLineGap
                canvas.drawText(text, x, baselineY, labelPaint)
            }
        }
    }

    private fun drawAxisPriceLabels(canvas: Canvas) {
        val formatter = formatAxisPrice ?: return
        val (minPrice, maxPrice) = chartDrawable.displayedPriceRange() ?: return

        labelPaint.color = context.textSecondaryColor
        drawEndAlignedPriceLabel(
            canvas,
            text = formatter(maxPrice),
            baselineY = maxPriceBaselineY()
        )
        drawEndAlignedPriceLabel(
            canvas,
            text = formatter(minPrice),
            baselineY = minPriceBaselineY()
        )
    }

    private fun drawEndAlignedPriceLabel(canvas: Canvas, text: String, baselineY: Float) {
        val x = width - paddingRight - axisPriceEndPadding - labelPaint.measureText(text)
        canvas.drawText(text, x, baselineY, labelPaint)
    }

    private fun timeAxisLabelBaselineY(): Float {
        val fm = labelPaint.fontMetrics
        val bottomEdge = height - paddingBottom.toFloat()
        return bottomEdge - timeAxisLabelBottom - fm.descent
    }

    private fun maxPriceBaselineY(): Float {
        return paddingTop - labelPaint.fontMetrics.ascent
    }

    private fun minPriceBaselineY(): Float {
        return height - paddingBottom - axisPriceMinBottomPadding - labelPaint.fontMetrics.descent
    }

    private fun updateTouch(x: Float) {
        val selected = findPointAtX(x)
        selectPoint(selected)

        selected?.let { point ->
            touchIndicatorDrawable.setData(x, chartDrawable.getDotY(point))
        } ?: touchIndicatorDrawable.setData(-1f, -1f)
    }

    private fun findPointAtX(x: Float): ChartPoint? {
        val entities = chartDrawable.entities
        if (entities.isEmpty() || chartDrawable.stepX == 0f) return null

        val index = (x / chartDrawable.stepX).toInt().coerceIn(0, entities.lastIndex)
        return entities[index]
    }

    private fun selectPoint(point: ChartPoint?) {
        if (selectedPoint == point) return
        selectedPoint = point
        onPointSelected?.invoke(point)
    }

    private fun clearSelection(dispatchCallback: Boolean) {
        if (dispatchCallback) {
            selectPoint(null)
        } else {
            selectedPoint = null
        }
        touchIndicatorDrawable.setData(-1f, -1f)
    }

    private fun List<ChartPoint>.toChartPayload(): List<ChartPoint> {
        return when {
            isEmpty() -> emptyList()
            size > MIN_EXPANDED_POINTS -> this
            else -> buildList(size * SMALL_DATA_REPEAT_COUNT) {
                for (point in this@toChartPayload) {
                    repeat(SMALL_DATA_REPEAT_COUNT) { add(point) }
                }
            }
        }
    }

    private fun android.graphics.Rect.matches(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Boolean {
        return this.left == left && this.top == top && this.right == right && this.bottom == bottom
    }

    private inline fun TextPaint.withTextAlign(align: Paint.Align, block: () -> Unit) {
        val previous = textAlign
        textAlign = align
        try {
            block()
        } finally {
            textAlign = previous
        }
    }

    private companion object {
        private const val MIN_EXPANDED_POINTS = 100
        private const val SMALL_DATA_REPEAT_COUNT = 4
        private val TIME_LABEL_GUIDE_INDICES = intArrayOf(0, 2)
    }
}