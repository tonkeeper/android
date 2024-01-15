package blur.node.legacy

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import blur.SimpleCanvas

class SnapshotCanvas(
    val width: Int,
    val height: Int,
) {

    private val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = SimpleCanvas(bitmap)

    private var translateX = 0f
    private var translateY = 0f
    private var scaleX = 0f
    private var scaleY = 0f

    fun capture(source: (output: Canvas) -> Unit): Bitmap {
        bitmap.eraseColor(Color.TRANSPARENT)
        val save = canvas.save()
        canvas.scale(scaleX, scaleY)
        canvas.translate(translateX, translateY)
        source(canvas)
        canvas.restoreToCount(save)
        return bitmap
    }

    fun setTranslate(x: Float, y: Float) {
        translateX = x
        translateY = y
    }

    fun setScale(x: Float, y: Float) {
        scaleX = x
        scaleY = y
    }

    fun release() {
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}