package uikit.drawable

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import uikit.R
import uikit.base.BaseDrawable
import uikit.extensions.dp
import uikit.extensions.getDimension
import uikit.list.BaseListItem
import uikit.list.ListCell

class CellBackgroundDrawable(
    context: Context,
    private val position: ListCell.Position,
    backgroundColor: Int = context.getColor(R.color.backgroundContent)
): BaseDrawable() {

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
            backgroundColor: Int = context.getColor(R.color.backgroundContent)
        ): Drawable {
            val color = context.getColor(R.color.backgroundHighlighted)
            return RippleDrawable(
                ColorStateList.valueOf(color),
                CellBackgroundDrawable(context, position, backgroundColor),
                null
            )
        }
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor
    }
    private val radius = context.getDimension(R.dimen.cornerMedium)
    private val firstCorners = createCorners(radius, radius, 0f, 0f)
    private val lastCorners = createCorners(0f, 0f, radius, radius)
    private val singleCorners = createCorners(radius, radius, radius, radius)

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.separatorCommon)
    }

    private val dividerHeight = .5f.dp
    private val dividerMarginStart = 16f.dp
    private val path = Path()

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, backgroundPaint)
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
        when (position) {
            ListCell.Position.FIRST -> firstPath(bounds)
            ListCell.Position.MIDDLE -> middlePath(bounds)
            ListCell.Position.LAST -> lastPath(bounds)
            ListCell.Position.SINGLE -> singlePath(bounds)
        }
    }
}