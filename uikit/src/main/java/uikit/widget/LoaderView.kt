package uikit.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import uikit.R
import uikit.extensions.dp
import uikit.extensions.useAttributes

class LoaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    var type = TYPE_DEFAULT
    private val paint: Paint
    private var size = 0
    private var progress = 0.4f
    private val bounds: RectF
    private var indeterminateSweep = 0f
    private var indeterminateRotateOffset = 0f
    private var thickness: Int
    private var color = 0
    private val animDuration: Int
    private val animSteps: Int
    private var startAngle: Float
    private var startAngleRotate: ValueAnimator? = null
    private var progressAnimator: ValueAnimator? = null
    private var indeterminateAnimator: AnimatorSet? = null
    private var animator: ObjectAnimator? = null

    init {
        thickness = 2.dp
        startAngle = -90f
        animDuration = 4000
        animSteps = 3
        paint = Paint(Paint.ANTI_ALIAS_FLAG)
        bounds = RectF()
        updatePaint()

        context.useAttributes(attrs, R.styleable.LoaderView) {
            val color = it.getColor(R.styleable.LoaderView_android_color, context.getColor(R.color.iconSecondary))
            setColor(color)
        }
    }

    fun setProgressType(type: Int) {
        if (this.type == type) {
            return
        }
        this.type = type
        updatePaint()
        stopAnimation()
        resetAnimation()
    }

    fun setProgressAnimation(progress: Float) {
        if (animator != null) {
            animator!!.cancel()
        }
        animator = ObjectAnimator.ofFloat(this, "progress", this.progress, progress)
        animator?.duration = 320
        animator?.start()
    }

    fun getProgress(): Float {
        return progress
    }

    fun setProgress(progress: Float) {
        this.progress = progress
        invalidate()
    }

    fun setSize(size: Int) {
        thickness = size
        updatePaint()
    }

    fun setColor(color: Int) {
        this.color = color
        updatePaint()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val xPad = paddingLeft + paddingRight
        val yPad = paddingTop + paddingBottom
        val width = measuredWidth - xPad
        val height = measuredHeight - yPad
        size = width.coerceAtMost(height)
        setMeasuredDimension(size + xPad, size + yPad)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        size = w.coerceAtMost(h)
        updateBounds()
    }

    private fun updateBounds() {
        val paddingLeft = paddingLeft
        val paddingTop = paddingTop
        bounds[(paddingLeft + thickness).toFloat(), (paddingTop + thickness).toFloat(), (size - paddingLeft - thickness).toFloat()] =
            (size - paddingTop - thickness).toFloat()
    }

    private fun updatePaint() {
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = thickness.toFloat()
        paint.strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (type) {
            TYPE_DEFAULT -> canvas.drawArc(
                bounds,
                startAngle + indeterminateRotateOffset,
                indeterminateSweep,
                false,
                paint
            )
            TYPE_PROGRESS -> {
                val angle = 360 * progress
                canvas.drawArc(bounds, startAngle + indeterminateRotateOffset, angle, false, paint)
            }
        }
    }

    fun resetAnimation() {
        if (type == TYPE_PROGRESS) {
            val rotateAnimator = ValueAnimator.ofFloat(0f, 360f)
            rotateAnimator.duration = 2000
            rotateAnimator.repeatCount = ValueAnimator.INFINITE
            rotateAnimator.interpolator = LinearInterpolator()
            rotateAnimator.addUpdateListener { animation: ValueAnimator ->
                indeterminateRotateOffset = animation.animatedValue as Float
                invalidate()
            }
            rotateAnimator.start()
            return
        }
        if (startAngleRotate != null && startAngleRotate!!.isRunning) {
            startAngleRotate!!.cancel()
        }
        if (progressAnimator != null && progressAnimator!!.isRunning) {
            progressAnimator!!.cancel()
        }
        if (indeterminateAnimator != null && indeterminateAnimator!!.isRunning) {
            indeterminateAnimator!!.cancel()
        }
        indeterminateSweep = INDETERMINANT_MIN_SWEEP
        indeterminateAnimator = AnimatorSet()
        var prevSet: AnimatorSet? = null
        var nextSet: AnimatorSet
        for (k in 0 until animSteps) {
            nextSet = createIndeterminateAnimator(k.toFloat())
            val builder = indeterminateAnimator!!.play(nextSet)
            if (prevSet != null) {
                builder.after(prevSet)
            }
            prevSet = nextSet
        }
        indeterminateAnimator!!.addListener(object : AnimatorListenerAdapter() {
            var wasCancelled = false
            override fun onAnimationCancel(animation: Animator) {
                wasCancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!wasCancelled) {
                    resetAnimation()
                }
            }
        })
        indeterminateAnimator!!.start()
    }

    fun stopAnimation() {
        if (startAngleRotate != null) {
            startAngleRotate!!.cancel()
            startAngleRotate = null
        }
        if (progressAnimator != null) {
            progressAnimator!!.cancel()
            progressAnimator = null
        }
        if (indeterminateAnimator != null) {
            indeterminateAnimator!!.cancel()
            indeterminateAnimator = null
        }
    }

    private fun createIndeterminateAnimator(step: Float): AnimatorSet {
        val maxSweep = 360f * (animSteps - 1) / animSteps + INDETERMINANT_MIN_SWEEP
        val start = -90f + step * (maxSweep - INDETERMINANT_MIN_SWEEP)
        val frontEndExtend = ValueAnimator.ofFloat(INDETERMINANT_MIN_SWEEP, maxSweep)
        frontEndExtend.duration = (animDuration / animSteps / 2).toLong()
        frontEndExtend.interpolator = DecelerateInterpolator(1f)
        frontEndExtend.addUpdateListener { animation: ValueAnimator ->
            indeterminateSweep = animation.animatedValue as Float
            invalidate()
        }
        val rotateAnimator1 =
            ValueAnimator.ofFloat(step * 720f / animSteps, (step + .5f) * 720f / animSteps)
        rotateAnimator1.duration = (animDuration / animSteps / 2).toLong()
        rotateAnimator1.interpolator = LinearInterpolator()
        rotateAnimator1.addUpdateListener { animation: ValueAnimator ->
            indeterminateRotateOffset = animation.animatedValue as Float
        }
        val backEndRetract =
            ValueAnimator.ofFloat(start, start + maxSweep - INDETERMINANT_MIN_SWEEP)
        backEndRetract.duration = (animDuration / animSteps / 2).toLong()
        backEndRetract.interpolator = DecelerateInterpolator(1f)
        backEndRetract.addUpdateListener { animation: ValueAnimator ->
            startAngle = animation.animatedValue as Float
            indeterminateSweep = maxSweep - startAngle + start
            invalidate()
        }
        val rotateAnimator2 =
            ValueAnimator.ofFloat((step + .5f) * 720f / animSteps, (step + 1) * 720f / animSteps)
        rotateAnimator2.duration = (animDuration / animSteps / 2).toLong()
        rotateAnimator2.interpolator = LinearInterpolator()
        rotateAnimator2.addUpdateListener { animation: ValueAnimator ->
            indeterminateRotateOffset = animation.animatedValue as Float
        }
        val set = AnimatorSet()
        set.play(frontEndExtend).with(rotateAnimator1)
        set.play(backEndRetract).with(rotateAnimator2).after(rotateAnimator1)
        return set
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resetAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    override fun setVisibility(visibility: Int) {
        val currentVisibility = getVisibility()
        super.setVisibility(visibility)
        if (visibility != currentVisibility) {
            if (visibility == VISIBLE) {
                resetAnimation()
            } else if (visibility == GONE || visibility == INVISIBLE) {
                stopAnimation()
            }
        }
    }

    companion object {
        private const val INDETERMINANT_MIN_SWEEP = 15f
        const val TYPE_PROGRESS = 1
        const val TYPE_DEFAULT = 2
    }

}