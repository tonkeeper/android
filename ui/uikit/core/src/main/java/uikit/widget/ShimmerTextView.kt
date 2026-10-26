package uikit.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.animation.doOnEnd
import androidx.core.graphics.toColorInt
import com.tonapps.uikit.color.backgroundPageColor
import uikit.extensions.dp
import uikit.extensions.isDark
import uikit.extensions.withAlpha
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.tan

class ShimmerTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyle) {

    private val dimColor = context.backgroundPageColor
    private val highlightColor = if (dimColor.isDark()) {
        SHIMMER_HIGHLIGHT_DARK.toColorInt()
    } else {
        SHIMMER_HIGHLIGHT_LIGHT.toColorInt()
    }
    private val skew = tan(Math.toRadians(SHIMMER_SKEW_DEGREES)).toFloat()
    private val dimPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
    private val bandPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
    private val bandMatrix = Matrix()

    private var fade = 0f
    private var phase = 0f
    private var shownAt = 0L
    private var fadeAnimator: ValueAnimator? = null
    private var sweepAnimator: ValueAnimator? = null
    private var swapAnimator: ValueAnimator? = null
    private var swapTarget: CharSequence? = null

    private val showShimmer = Runnable {
        shownAt = SystemClock.uptimeMillis()
        animateFade(1f)
        startSweep()
    }

    private val hideShimmer = Runnable {
        shownAt = 0L
        animateFade(0f)
    }

    var shimmerActive: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (value) {
                removeCallbacks(hideShimmer)
                if (shownAt == 0L) {
                    postDelayed(showShimmer, SHIMMER_DELAY_MS)
                }
            } else {
                removeCallbacks(showShimmer)
                if (shownAt != 0L) {
                    val owed = SHIMMER_PASS_MS - (SystemClock.uptimeMillis() - shownAt)
                    postDelayed(hideShimmer, owed.coerceAtLeast(0L))
                }
            }
        }

    fun setTextWithBlur(value: CharSequence) {
        if (TextUtils.equals(swapTarget ?: text, value)) {
            return
        }
        swapAnimator?.cancel()
        if (!isAttachedToWindow || text.isNullOrEmpty()) {
            text = value
            return
        }
        swapTarget = value
        var swapped = false
        swapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SWAP_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                if (!swapped && fraction >= 0.5f) {
                    swapped = true
                    text = value
                }
                val hidden = if (fraction < 0.5f) {
                    fraction * 2f
                } else {
                    (1f - fraction) * 2f
                }
                alpha = 1f - hidden
                applyBlur(hidden * SWAP_BLUR_RADIUS.dp)
            }
            doOnEnd {
                if (!swapped) {
                    text = value
                }
                swapTarget = null
                alpha = 1f
                applyBlur(0f)
            }
            start()
        }
    }

    fun cancelTextSwap() {
        swapAnimator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        if (fade <= 0f || width == 0 || height == 0) {
            super.onDraw(canvas)
            return
        }
        val left = scrollX.toFloat()
        val top = scrollY.toFloat()
        val right = left + width
        val bottom = top + height
        val layer = canvas.saveLayer(left, top, right, bottom, null)
        super.onDraw(canvas)

        dimPaint.color = dimColor.withAlpha(SHIMMER_DIM_OPACITY * fade)
        canvas.drawRect(left, top, right, bottom, dimPaint)

        val bandWidth = max(width.toFloat(), 1f) * SHIMMER_BAND_WIDTH_FACTOR
        bandMatrix.setTranslate(left - bandWidth + (width + bandWidth) * phase, top)
        bandPaint.shader?.setLocalMatrix(bandMatrix)
        bandPaint.alpha = (fade * 255).roundToInt()
        if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            canvas.scale(-1f, 1f, (left + right) / 2f, (top + bottom) / 2f)
        }
        canvas.drawRect(left, top, right, bottom, bandPaint)
        canvas.restoreToCount(layer)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val bandWidth = max(w.toFloat(), 1f) * SHIMMER_BAND_WIDTH_FACTOR
        val step = bandWidth / (1f + skew * skew)
        bandPaint.shader = LinearGradient(
            0f,
            0f,
            step,
            step * skew,
            ShimmerBellAlphas.map { highlightColor.withAlpha(it) }.toIntArray(),
            ShimmerBellPositions,
            Shader.TileMode.CLAMP,
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (shimmerActive && shownAt == 0L) {
            postDelayed(showShimmer, SHIMMER_DELAY_MS)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(showShimmer)
        removeCallbacks(hideShimmer)
        fadeAnimator?.cancel()
        sweepAnimator?.cancel()
        sweepAnimator = null
        swapAnimator?.cancel()
        shownAt = 0L
        fade = 0f
    }

    private fun animateFade(target: Float) {
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(fade, target).apply {
            duration = SHIMMER_FADE_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                fade = it.animatedValue as Float
                invalidate()
            }
            doOnEnd {
                if (fade <= 0f) {
                    sweepAnimator?.cancel()
                    sweepAnimator = null
                }
            }
            start()
        }
    }

    private fun startSweep() {
        if (sweepAnimator?.isRunning == true) {
            return
        }
        val passShare = SHIMMER_PASS_MS.toFloat() / (SHIMMER_PASS_MS + SHIMMER_PAUSE_MS)
        val passEasing = PathInterpolator(0.65f, 0f, 0.35f, 1f)
        sweepAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SHIMMER_PASS_MS + SHIMMER_PAUSE_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val progress = it.animatedValue as Float
                phase = if (progress < passShare) {
                    passEasing.getInterpolation(progress / passShare)
                } else {
                    1f
                }
                invalidate()
            }
            start()
        }
    }

    private fun applyBlur(radius: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        setRenderEffect(
            if (radius > 0.5f) {
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL)
            } else {
                null
            }
        )
    }
}

private const val SHIMMER_DELAY_MS = 300L
private const val SHIMMER_PASS_MS = 1400L
private const val SHIMMER_PAUSE_MS = 200L
private const val SHIMMER_FADE_MS = 250L
private const val SHIMMER_DIM_OPACITY = 0.6f
private const val SHIMMER_BAND_WIDTH_FACTOR = 0.7f
private const val SHIMMER_SKEW_DEGREES = 10.0
private const val SHIMMER_HIGHLIGHT_DARK = "#C2DAFF"
private const val SHIMMER_HIGHLIGHT_LIGHT = "#1F5FBF"
private const val SWAP_DURATION_MS = 350L
private const val SWAP_BLUR_RADIUS = 8f

private val ShimmerBellAlphas = floatArrayOf(0f, 0.08f, 0.38f, 0.98f, 0.38f, 0.08f, 0f)
private val ShimmerBellPositions = FloatArray(ShimmerBellAlphas.size) { it / (ShimmerBellAlphas.size - 1f) }
