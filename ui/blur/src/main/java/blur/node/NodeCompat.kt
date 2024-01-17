package blur.node

import android.graphics.Canvas
import android.graphics.RectF

internal abstract class NodeCompat(name: String) {

    val bounds = RectF()

    abstract fun draw(input: Canvas, drawOutput: (output: Canvas) -> Unit)

    fun setBounds(rect: RectF) {
        bounds.set(rect)
        onBoundsChange(bounds)
    }

    open fun onBoundsChange(bounds: RectF) {

    }

    open fun drawRecorded(canvas: Canvas) {

    }

}