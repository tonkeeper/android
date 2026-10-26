package uikit.drawable

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.core.animation.doOnEnd
import androidx.core.graphics.withSave
import com.tonapps.uikit.color.backgroundContentColor
import com.tonapps.uikit.color.backgroundHighlightedColor
import com.tonapps.uikit.color.separatorCommonColor
import com.tonapps.uikit.list.ListCell
import uikit.R
import uikit.base.BaseDrawable
import uikit.extensions.applyPressScale
import uikit.extensions.applyPressScaleContent
import uikit.extensions.dp
import uikit.extensions.getDimension

class CellBackgroundDrawable(
    private val context: Context,
    val position: ListCell.Position,
    private val backgroundColor: Int = context.backgroundContentColor,
    private val shimmerColor: Int = context.backgroundHighlightedColor
): BaseDrawable(), Animatable {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor
    }

    private val shimmerPaint: Paint by lazy {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f,0f, shimmerSize, 0f, intArrayOf(Color.TRANSPARENT, shimmerColor, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)

        paint
    }

    private val shimmerSize = 102f.dp
    private var shimmerOffsetX = 0f
    private val shimmerAnimator: ValueAnimator by lazy {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 860
            addUpdateListener {
                setShimmerProgress(it.animatedFraction)
            }
            doOnEnd {
                isShimmerAnimation = false
            }
        }
    }

    private var isShimmerAnimation = false

    private val radius = context.getDimension(R.dimen.cornerMedium)
    private val firstCorners = createCorners(radius, radius, 0f, 0f)
    private val lastCorners = createCorners(0f, 0f, radius, radius)
    private val singleCorners = createCorners(radius, radius, radius, radius)

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.separatorCommonColor
    }

    private val dividerHeight = .5f.dp
    private val dividerMarginStart = 16f.dp
    private val path = Path()

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, backgroundPaint)
        if (isShimmerAnimation) {
            canvas.withSave {
                translate(shimmerOffsetX, 0f)
                drawPath(path, shimmerPaint)
            }
        }
        if (position != ListCell.Position.SINGLE && position != ListCell.Position.LAST) {
            drawDivider(canvas, bounds)
        }
    }

    private fun drawDivider(canvas: Canvas, bounds: Rect) {
        canvas.drawRect(
            bounds.left + dividerMarginStart,
            bounds.bottom - dividerHeight,
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
            dividerPaint
        )
    }

    private fun firstPath(bounds: Rect) {
        path.reset()
        path.addRoundRect(
            RectF(bounds),
            firstCorners,
            Path.Direction.CW
        )
    }

    private fun middlePath(bounds: Rect) {
        path.reset()
        path.addRect(RectF(bounds), Path.Direction.CW)
    }

    private fun lastPath(bounds: Rect) {
        path.reset()
        path.addRoundRect(
            RectF(bounds),
            lastCorners,
            Path.Direction.CW
        )
    }

    private fun singlePath(bounds: Rect) {
        path.reset()
        path.addRoundRect(
            RectF(bounds),
            singleCorners,
            Path.Direction.CW
        )
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        // onBoundsChange fires on every layout pass; the press-scale helpers guard themselves via a
        // view tag (see PressScale.kt), so attaching here every pass binds only once per host view.
        // A grouped cell scales only its content so this shared background stays put and neighbouring
        // cells keep forming one continuous card; a SINGLE cell is a standalone card and scales as a
        // whole, background included.
        when (val host = callback) {
            is ViewGroup -> if (position == ListCell.Position.SINGLE) {
                host.applyPressScale()
            } else {
                host.applyPressScaleContent()
            }
            is View -> host.applyPressScale()
        }
        when (position) {
            ListCell.Position.FIRST -> firstPath(bounds)
            ListCell.Position.MIDDLE -> middlePath(bounds)
            ListCell.Position.LAST -> lastPath(bounds)
            ListCell.Position.SINGLE -> singlePath(bounds)
        }
    }

    private fun setShimmerProgress(progress: Float) {
        if (isShimmerAnimation) {
            shimmerOffsetX = bounds.width() * progress
            if (shimmerOffsetX > bounds.width()) {
                stop()
            } else {
                invalidateSelf()
            }
        }
    }

    override fun start() {
        if (!isShimmerAnimation) {
            isShimmerAnimation = true
            shimmerAnimator.start()
            invalidateSelf()
        }
    }

    override fun stop() {
        if (isShimmerAnimation) {
            isShimmerAnimation = false
            shimmerAnimator.end()
            invalidateSelf()
        }
    }

    override fun isRunning() = isShimmerAnimation && shimmerAnimator.isRunning

    companion object {
        private fun createCorners(
            topLeft: Float,
            topRight: Float,
            bottomRight: Float,
            bottomLeft: Float
        ): FloatArray {
            return floatArrayOf(
                topLeft, topLeft,
                topRight, topRight,
                bottomRight, bottomRight,
                bottomLeft, bottomLeft
            )
        }

        fun create(
            context: Context,
            position: ListCell.Position,
            backgroundColor: Int = context.backgroundContentColor
        ): Drawable {
            return CellBackgroundDrawable(context, position, backgroundColor)
        }

        fun find(view: View): CellBackgroundDrawable? {
            return view.background as? CellBackgroundDrawable
        }
    }
}