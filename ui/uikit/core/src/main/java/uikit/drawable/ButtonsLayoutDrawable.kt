package uikit.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import com.tonapps.uikit.color.backgroundContentColor
import com.tonapps.uikit.color.separatorCommonColor
import uikit.base.BaseDrawable
import uikit.extensions.dp

class ButtonsLayoutDrawable(context: Context): BaseDrawable() {

    private val lineColor = context.separatorCommonColor

    private val horizontalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = .5f.dp
        style = Paint.Style.STROKE
    }

    private val verticalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = .5f.dp
        style = Paint.Style.STROKE
    }

    private val gradientColors = intArrayOf(
        0, lineColor
    )

    private val gradientPositions = floatArrayOf(
        .05f, .5f
    )

    var rowCount: Int = 0
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    var columnCount: Int = 0
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    override fun draw(canvas: Canvas) {
        for (i in 1 until rowCount) {
            val y = i * bounds.height() / rowCount
            canvas.drawLine(0f, y.toFloat(), bounds.width().toFloat(), y.toFloat(), horizontalPaint)
        }

        for (i in 1 until columnCount) {
            val x = i * bounds.width() / columnCount
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), bounds.height().toFloat(), verticalPaint)
        }
    }

    override fun onBoundsChange(rect: Rect) {
        super.onBoundsChange(rect)
        horizontalPaint.shader = LinearGradient(
            0f, 0f, rect.width() / 2f, 0f,
            gradientColors,
            gradientPositions,
            Shader.TileMode.MIRROR
        )

        verticalPaint.shader = LinearGradient(
            0f, 0f, 0f, rect.height() / 2f,
            gradientColors,
            gradientPositions,
            Shader.TileMode.MIRROR
        )
    }
}