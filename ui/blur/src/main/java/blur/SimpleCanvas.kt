package blur

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.text.MeasuredText
import android.os.Build
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

    override fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: RectF, paint: Paint?) {
        super.drawBitmap(optimizeBitmap(bitmap), src, dst, paint)
    }

    override fun drawBitmap(bitmap: Bitmap, src: Rect?, dst: Rect, paint: Paint?) {
        super.drawBitmap(optimizeBitmap(bitmap), src, dst, paint)
    }

    override fun drawBitmap(bitmap: Bitmap, matrix: Matrix, paint: Paint?) {
        super.drawBitmap(optimizeBitmap(bitmap), matrix, paint)
    }

    override fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) {
        super.drawBitmap(optimizeBitmap(bitmap), left, top, paint)
    }

    private companion object {

        fun optimizeBitmap(bitmap: Bitmap): Bitmap {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, bitmap.isMutable)
            } else {
                bitmap
            }
        }
    }

}