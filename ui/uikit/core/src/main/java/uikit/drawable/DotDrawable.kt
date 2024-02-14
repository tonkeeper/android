package uikit.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.tonapps.uikit.color.UIKitColor
import uikit.base.BaseDrawable
import uikit.extensions.dp

class DotDrawable(context: Context): BaseDrawable() {

    companion object {
        val size = 6.dp
    }


    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColor(UIKitColor.accentRed)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), bounds.width() / 2f, paint)
    }

    override fun getIntrinsicWidth(): Int {
        return size
    }

    override fun getIntrinsicHeight(): Int {
        return size
    }

    override fun getMinimumHeight(): Int {
        return size
    }

    override fun getMinimumWidth(): Int {
        return size
    }

}