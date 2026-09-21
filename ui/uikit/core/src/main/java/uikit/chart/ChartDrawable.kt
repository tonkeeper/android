package uikit.chart

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Shader
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.graphics.withTranslation
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.accentRedColor
import com.tonapps.uikit.color.separatorCommonColor
import uikit.extensions.dp
import uikit.extensions.withAlpha
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

class ChartDrawable(context: Context) : BaseChartDrawable(context) {

    private companion object {
        const val VERTICAL_TIME_LINES = 5
        private const val FILL_GRADIENT_ALPHA = 76
        private const val MORPH_SAMPLES_MIN = 48
        private const val MORPH_SAMPLES_MAX = 160
        private const val MORPH_FINISH_EPSILON = 1e-4f
        private const val MIN_SEGMENT_WIDTH = 1e-6f
        private const val MORPH_DURATION_MS = 200L
    }

    private data class PriceRange(
        val min: Float = 0f,
        val max: Float = 1f,
    ) {
        val value: Float
            get() = kotlin.math.max(max - min, Float.MIN_VALUE)
    }

    private val positiveColor = context.accentGreenColor
    private val negativeColor = context.accentRedColor
    private var chartColor = accentColor

    private var data: List<ChartPoint> = emptyList()
    private var dataPriceRange = PriceRange()
    private var morphPriceRange = PriceRange()
    private var morphFromData: List<ChartPoint>? = null
    private var transitionT = 1f
    private var transitionAnimator: ValueAnimator? = null

    private val linePath = Path()
    private val fillPath = Path()
    private var chartPathsValid = false

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f.dp
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = chartColor
        strokeWidth = strokeSize
        style = Paint.Style.STROKE
    }

    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = chartColor
        style = Paint.Style.FILL
    }

    var verticalGuidesBottomExtraPx: Float = 0f

    val entities: List<ChartPoint>
        get() = data.toList()

    val stepX: Float
        get() = if (data.isEmpty()) 0f else chartWidth / data.size

    val isEmpty: Boolean
        get() = data.isEmpty()

    fun displayedPriceRange(): Pair<Float, Float>? {
        val prices = data.asSequence()
            .filterNot { it.isEmpty }
            .map { it.price }
            .toList()

        val min = prices.minOrNull() ?: return null
        val max = prices.maxOrNull() ?: return null
        return min to max
    }

    @Suppress("UNUSED_PARAMETER")
    fun setData(data: List<ChartPoint>, isSquare: Boolean) {
        stopMorphAnimation()
        resetMorphState(rebuildPaths = this.data.isNotEmpty())

        val oldData = this.data
        val canMorph = oldData.isNotEmpty() && data.isNotEmpty() && oldData != data

        this.data = data
        invalidatePaths()

        if (data.isEmpty()) {
            resetMorphState(rebuildPaths = false)
            invalidateSelf()
            return
        }

        dataPriceRange = computePriceRange(data)
        updateChartColor()

        if (canMorph) {
            startMorphAnimation(fromData = oldData)
        } else {
            resetMorphState(rebuildPaths = true)
        }

        invalidateSelf()
    }

    fun getDotY(point: ChartPoint): Float {
        if (data.isEmpty()) return chartHeight / 2f
        val y = yFromPrice(point.price, dataPriceRange)
        return y.takeIf { it.isFinite() } ?: chartHeight / 2f
    }

    fun verticalGuideAnchorX(index: Int): Float {
        if (data.isEmpty() || index !in 0 until VERTICAL_TIME_LINES) return 0f
        return bounds.left + verticalGuideLocalX(index)
    }

    fun verticalGuideTimestamp(index: Int): Long? {
        if (data.isEmpty() || index !in 0 until VERTICAL_TIME_LINES) return null
        return timestampAtChartX(verticalGuideLocalX(index))
    }

    override fun draw(canvas: Canvas) {
        if (data.isEmpty()) return

        rebuildPathsIfNeeded()
        gridPaint.color = context.separatorCommonColor

        canvas.withTranslation(bounds.left.toFloat(), bounds.top + strokeSize) {
            drawVerticalTimeGuides(this)
            drawPath(fillPath, gradientPaint)
            drawPath(linePath, linePaint)
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        updateGradient()
        stopMorphAnimation()
        resetMorphState(rebuildPaths = data.isNotEmpty())
    }

    private fun startMorphAnimation(fromData: List<ChartPoint>) {
        morphPriceRange = computePriceRange(fromData)
        morphFromData = fromData.toList()
        transitionT = 0f
        buildMorphPaths()

        transitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MORPH_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                transitionT = animator.animatedValue as Float
                invalidateSelf()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    transitionAnimator = null
                    resetMorphState(rebuildPaths = true)
                    invalidateSelf()
                }
            })
            start()
        }
    }

    private fun stopMorphAnimation() {
        transitionAnimator?.let { animator ->
            animator.removeAllUpdateListeners()
            animator.removeAllListeners()
            animator.cancel()
        }
        transitionAnimator = null
    }

    private fun resetMorphState(rebuildPaths: Boolean) {
        morphFromData = null
        transitionT = 1f

        if (rebuildPaths) {
            buildPaths()
        } else {
            invalidatePaths()
        }
    }

    private fun rebuildPathsIfNeeded() {
        val isMorphing = morphFromData != null && transitionT < 1f - MORPH_FINISH_EPSILON
        when {
            isMorphing -> buildMorphPaths()
            !chartPathsValid -> buildPaths()
        }
    }

    private fun invalidatePaths() {
        linePath.reset()
        fillPath.reset()
        chartPathsValid = false
    }

    private fun updateChartColor() {
        val color = resolveChartColor()
        if (chartColor == color) return

        chartColor = color
        linePaint.color = color
        gradientPaint.color = color
        updateGradient()
    }

    private fun resolveChartColor(): Int {
        val prices = data.asSequence()
            .filterNot { it.isEmpty }
            .map { it.price }
            .toList()
        if (prices.size < 2) return chartColor

        return if (prices.last() < prices.first()) negativeColor else positiveColor
    }

    private fun updateGradient() {
        gradientPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            chartHeight,
            intArrayOf(
                chartColor.withAlpha(FILL_GRADIENT_ALPHA),
                chartColor.withAlpha(52),
                chartColor.withAlpha(30),
                chartColor.withAlpha(14),
                chartColor.withAlpha(4),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.22f, 0.44f, 0.66f, 0.86f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    private fun buildPaths() {
        invalidatePaths()

        val points = data.mapIndexed { index, point ->
            PointF(index * stepX, yFromPrice(point.price, dataPriceRange))
        }
        if (points.isEmpty()) return

        buildLineAndFillPaths(points)
    }

    private fun buildMorphPaths() {
        val from = morphFromData ?: run {
            buildPaths()
            return
        }

        invalidatePaths()

        val t = transitionT.coerceIn(0f, 1f)
        val samples = morphSamplesCount(from, data)
        val points = buildList(samples + 1) {
            for (i in 0..samples) {
                val x = chartWidth * (i / samples.toFloat())
                add(PointF(x, morphYAt(x, from, t)))
            }
        }

        buildLineAndFillPaths(points, fillRightX = chartWidth, fillLeftX = 0f)
    }

    private fun buildLineAndFillPaths(
        points: List<PointF>,
        fillRightX: Float = points.last().x,
        fillLeftX: Float = points.first().x,
    ) {
        val first = points.first()
        linePath.moveTo(first.x, first.y)
        fillPath.moveTo(first.x, first.y)

        for (i in 1 until points.size) {
            val point = points[i]
            linePath.lineTo(point.x, point.y)
            fillPath.lineTo(point.x, point.y)
        }

        val fillBottomY = chartHeight + strokeSize * 2
        fillPath.lineTo(fillRightX, fillBottomY)
        fillPath.lineTo(fillLeftX, fillBottomY)
        fillPath.close()
        chartPathsValid = true
    }

    private fun drawVerticalTimeGuides(canvas: Canvas) {
        val bottomY = chartHeight + strokeSize * 2 + verticalGuidesBottomExtraPx
        for (index in 0 until VERTICAL_TIME_LINES) {
            val x = verticalGuideLocalX(index)
            val yTop = lineYAtChartX(x)
            if (yTop.isFinite()) {
                canvas.drawLine(x, yTop, x, bottomY, gridPaint)
            }
        }
    }

    private fun verticalGuideLocalX(index: Int): Float {
        return (index + 0.5f) * (chartWidth / VERTICAL_TIME_LINES)
    }

    private fun timestampAtChartX(x: Float): Long? {
        if (data.isEmpty()) return null
        if (data.size == 1) return data.first().date.takeIf { it > 0 }

        val segment = segmentAtX(data.size, x)
        val d0 = data[segment.fromIndex].date
        val d1 = data[segment.toIndex].date

        return when {
            d0 <= 0L && d1 <= 0L -> null
            d0 <= 0L -> d1
            d1 <= 0L -> d0
            else -> (d0 + (d1 - d0) * segment.t).toLong()
        }
    }

    private fun lineYAtChartX(x: Float): Float {
        val from = morphFromData
        val t = transitionT

        return if (from != null && t < 1f - MORPH_FINISH_EPSILON) {
            morphYAt(x, from, t)
        } else {
            lineYFromSeries(data, x, dataPriceRange)
        }
    }

    private fun morphYAt(x: Float, fromData: List<ChartPoint>, t: Float): Float {
        val fromY = lineYFromSeries(fromData, x, morphPriceRange)
        val toY = lineYFromSeries(data, x, dataPriceRange)
        return fromY + (toY - fromY) * t
    }

    private fun lineYFromSeries(
        series: List<ChartPoint>,
        x: Float,
        priceRange: PriceRange,
    ): Float {
        if (series.isEmpty()) return chartHeight
        if (series.size == 1) return yFromPrice(series.first().price, priceRange)

        val segment = segmentAtX(series.size, x)
        val p0 = series[segment.fromIndex].price
        val p1 = series[segment.toIndex].price
        val price = p0 + (p1 - p0) * segment.t
        return yFromPrice(price, priceRange)
    }

    private fun yFromPrice(price: Float, range: PriceRange): Float {
        return chartHeight - ((price - range.min) / range.value) * chartHeight
    }

    private data class Segment(
        val fromIndex: Int,
        val toIndex: Int,
        val t: Float,
    )

    private fun segmentAtX(size: Int, x: Float): Segment {
        val step = chartWidth / size
        val idxFloat = x / step
        val i0 = floor(idxFloat).toInt().coerceIn(0, size - 2)
        val i1 = i0 + 1
        val x0 = i0 * step
        val x1 = i1 * step
        val t = if (abs(x1 - x0) < MIN_SEGMENT_WIDTH) {
            0f
        } else {
            ((x - x0) / (x1 - x0)).coerceIn(0f, 1f)
        }
        return Segment(i0, i1, t)
    }

    private fun computePriceRange(series: List<ChartPoint>): PriceRange {
        if (series.isEmpty()) return PriceRange()

        var maxPrice = series.maxOf { it.price }
        var minPrice = series.minOf { it.price }

        if (maxPrice == minPrice) {
            maxPrice += 1f
            minPrice = 0f
        }

        return PriceRange(min = minPrice, max = maxPrice)
    }

    private fun morphSamplesCount(from: List<ChartPoint>, to: List<ChartPoint>): Int {
        return max(
            MORPH_SAMPLES_MIN,
            max(from.size, to.size) * 2,
        ).coerceAtMost(MORPH_SAMPLES_MAX)
    }
}