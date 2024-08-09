package com.tonapps.tonkeeper.ui.screen.signer.qr

import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.tonapps.tonkeeperx.R
import uikit.base.BaseDrawable
import uikit.extensions.dp
import uikit.extensions.getDimension

class QRBackground(context: Context): BaseDrawable() {

    private val hookSize = 42f.dp
    private val radius = context.getDimension(uikit.R.dimen.cornerMedium)
    private val color = context.getColor(R.color.constantQROrange)
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        paint.color = color
        paint.style = Paint.Style.FILL
        paint.pathEffect = CornerPathEffect(radius)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val rect = RectF(bounds)

        path.reset()
        path.moveTo(rect.left, rect.top)
        path.lineTo(rect.right, rect.top)
        path.lineTo(rect.right, rect.bottom - hookSize)
        path.lineTo(rect.right - hookSize, rect.bottom)
        path.lineTo(rect.left, rect.bottom)
        path.close()
    }
}