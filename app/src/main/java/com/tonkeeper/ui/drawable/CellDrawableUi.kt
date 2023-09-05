package com.tonkeeper.ui.drawable

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.extensions.createCorners
import com.tonkeeper.extensions.dp
import com.tonkeeper.ui.list.wallet.item.WalletCellItem

class CellDrawableUi(
    private val position: WalletCellItem.Position
): UiBaseDrawable() {

    private companion object {

        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = App.getColor(R.color.primary)
        }

        private val radius = App.getDimension(R.dimen.radius)
        private val firstCorners = createCorners(radius, radius, 0f, 0f)
        private val lastCorners = createCorners(0f, 0f, radius, radius)
        private val singleCorners = createCorners(radius, radius, radius, radius)

        private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = App.getColor(R.color.divider)
        }

        private val dividerHeight = .5f.dp
        private val dividerMarginStart = 16f.dp
    }

    private val path = Path()

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, backgroundPaint)
        if (position != WalletCellItem.Position.SINGLE && position != WalletCellItem.Position.LAST) {
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
            WalletCellItem.Position.FIRST -> firstPath(bounds)
            WalletCellItem.Position.MIDDLE -> middlePath(bounds)
            WalletCellItem.Position.LAST -> lastPath(bounds)
            WalletCellItem.Position.SINGLE -> singlePath(bounds)
        }
    }
}

