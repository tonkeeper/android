package com.tonapps.tonkeeper.ui.component.chart

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.view.animation.LinearInterpolator
import androidx.core.animation.doOnRepeat
import uikit.extensions.dp
import kotlin.math.sin

class LoadingDrawable(context: Context) : BaseChartDrawable(context) {
    private var path = Path()
    private val snakePoints = mutableListOf<PointF>()
    private val animationDuration = 1400L
    private var progress = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = animationDuration
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { animator ->
            progress = animator.animatedFraction
            if (progress <= 0.01f) {
                snakePoints.clear()
                path.reset()
            }
            updateSnake()
            invalidateSelf()
        }
        doOnRepeat {
            snakePoints.clear()
            path.reset()
        }
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        strokeWidth = strokeSize * 2
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, linePaint)
    }

    fun stopAnimation() {
        animator.cancel()
        resetSnake()
    }

    fun startAnimation() {
        if (!animator.isRunning) {
            resetSnake()
            animator.start()
        }
    }

    private fun resetSnake() {
        progress = 0f
        snakePoints.clear()
        path.reset()
    }

    private fun updateSnake() {
        val currentX = progress * chartWidth
        val newY = calculateNewY(progress)
        snakePoints.add(PointF(currentX, newY))

        path.reset()
        if (snakePoints.isNotEmpty()) {
            path.moveTo(snakePoints.first().x, snakePoints.first().y)
            for (i in 1 until snakePoints.size) {
                path.lineTo(snakePoints[i].x, snakePoints[i].y)
            }
        }
    }

    private fun calculateNewY(x: Float): Float {
        val baseY = chartHeight / 2
        val amplitude = chartHeight * 0.25f // Adjust this to change the vertical range
        val frequency = 3f // Adjust this to change how often the line moves up and down

        // Use multiple sine waves with different frequencies to create a more natural movement
        val y = baseY +
                amplitude * 0.6f * sin(x * frequency * 2 * Math.PI.toFloat()) +
                amplitude * 0.3f * sin(x * frequency * 4 * Math.PI.toFloat() + 0.5f) +
                amplitude * 0.1f * sin(x * frequency * 8 * Math.PI.toFloat() + 1.0f)

        return y.coerceIn(strokeSize, chartHeight - strokeSize)
    }
}