package blur

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Region
import android.graphics.text.MeasuredText
import android.util.Log

class SimpleCanvas(bitmap: Bitmap): Canvas(bitmap) {

    override fun drawTextRun(text: MeasuredText, start: Int, end: Int, contextStart: Int, contextEnd: Int, x: Float, y: Float, isRtl: Boolean, paint: Paint) {
        drawRect(x, y, x + paint.textSize * (end - start), y + paint.textSize, paint)
    }

    override fun drawTextRun(text: CharSequence, start: Int, end: Int, contextStart: Int, contextEnd: Int, x: Float, y: Float, isRtl: Boolean, paint: Paint) {
        drawRect(x, y, x + paint.textSize * (end - start), y + paint.textSize, paint)
    }

    override fun drawTextRun(text: CharArray, index: Int, count: Int, contextIndex: Int, contextCount: Int, x: Float, y: Float, isRtl: Boolean, paint: Paint) {
        drawRect(x, y, x + paint.textSize * count, y + paint.textSize, paint)
    }

    override fun drawTextOnPath(text: CharArray, index: Int, count: Int, path: Path,  hOffset: Float, vOffset: Float, paint: Paint) {
        drawRect(hOffset, vOffset, hOffset + paint.textSize * count, vOffset + paint.textSize, paint)
    }

    override fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
        drawRect(x, y, x + paint.textSize * (end - start), y + paint.textSize, paint)
    }

    override fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
        val right = paint.textSize * (end - start)
        val bottom = paint.textSize
        drawRect(x, y, x + right, y + bottom, paint)
    }

    override fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint) {
        drawRect(x, y, x + paint.textSize * count, y + paint.textSize, paint)
    }

    override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        drawRect(x, y, x + paint.textSize * text.length, y + paint.textSize, paint)
    }

    override fun drawTextOnPath(text: String, path: Path, hOffset: Float, vOffset: Float, paint: Paint) {
        drawRect(hOffset, vOffset, hOffset + paint.textSize * text.length, vOffset + paint.textSize, paint)
    }

    override fun clipPath(path: Path): Boolean {
        return false
    }

    override fun clipPath(path: Path, op: Region.Op): Boolean {
        return false
    }

}