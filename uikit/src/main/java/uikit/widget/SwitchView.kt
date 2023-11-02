package uikit.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.withSave
import androidx.core.view.isVisible
import uikit.ArgbEvaluator
import uikit.R
import uikit.extensions.dp
import uikit.extensions.getDrawable
import uikit.extensions.range

class SwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle), ValueAnimator.AnimatorUpdateListener {

    private companion object {
        private val evaluator = ArgbEvaluator.instance
        private val interpolator = AccelerateDecelerateInterpolator()

        private val radius = 100f.dp
        private val thumbSize = 26f.dp
        private val thumbOffset = 2.5f.dp
    }

    private val defaultTrackColor = context.getColor(R.color.buttonTertiaryBackground)
    private val activeTrackColor = context.getColor(R.color.buttonPrimaryBackground)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = defaultTrackColor
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.constantWhite)
        setShadowLayer(8f.dp, 0f, 3f.dp, Color.parseColor("#26000000"))
    }

    var checked: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                doCheckedChanged?.invoke(value)
                applyState(value)
            }
        }

    var doCheckedChanged: ((Boolean) -> Unit)? = null

    private var progress = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 80
        interpolator = SwitchView.interpolator
        addUpdateListener(this@SwitchView)
    }

    private val rect = RectF()
    private var offsetTop = 0f

    init {
        setOnClickListener {
            toggle()
        }
    }

    fun toggle() {
        checked = !checked
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawTrack(canvas)
        canvas.withSave {
            if (animator.isRunning) {
                val endTranslateX = if (checked) {
                    (width - thumbSize) - thumbOffset
                } else {
                    thumbOffset
                }

                val startTranslateX = if (checked) {
                    thumbOffset
                } else {
                    (width - thumbSize) - thumbOffset
                }

                val translateX = progress.range(startTranslateX, endTranslateX)

                canvas.translate(translateX, offsetTop)
            } else {
                val translateX = if (checked) {
                    (width - thumbSize) - thumbOffset
                } else {
                    thumbOffset
                }

                canvas.translate(translateX, offsetTop)
            }

            drawThumb(canvas)
        }
    }

    private fun drawTrack(canvas: Canvas) {
        canvas.drawRoundRect(
            rect,
            radius,
            radius,
            trackPaint
        )
    }

    private fun drawThumb(canvas: Canvas) {
        canvas.drawOval(
            0f,
            0f,
            thumbSize,
            thumbSize,
            thumbPaint
        )
    }

    private fun applyState(checked: Boolean) {
        if (isAttachedToWindow && isVisible) {
            animator.start()
        } else {
            if (checked) {
                progress = 0f
                trackPaint.color = activeTrackColor
            } else {
                progress = 0f
                trackPaint.color = defaultTrackColor
            }
            invalidate()
        }
    }

    override fun hasOverlappingRendering() = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(50.dp, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(30.dp, MeasureSpec.EXACTLY))
        rect.set(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat())
        offsetTop = (measuredHeight - thumbSize) / 2f
    }

    override fun onAnimationUpdate(animation: ValueAnimator) {
        progress = animation.animatedValue as Float

        trackPaint.color = if (checked) {
            evaluator.evaluate(progress, defaultTrackColor, activeTrackColor)
        } else {
            evaluator.evaluate(progress, activeTrackColor, defaultTrackColor)
        }


        invalidate()
    }
}