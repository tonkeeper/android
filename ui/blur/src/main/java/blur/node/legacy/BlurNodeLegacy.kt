package blur.node.legacy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import blur.Toolkit
import com.tonapps.ui.blur.R

internal class BlurNodeLegacy(
    private val context: Context
): BaseNode("blur") {

    private val noiseShader: BitmapShader by lazy {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.noise)
        BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = noiseShader
        xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
    }

    private var blurBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private var drawBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private var drawBitmapCanvas = Canvas(drawBitmap)
    private var drawBitmapRect = RectF()

    fun setSnapshot(bitmap: Bitmap) {
        checkBlurBitmap(bitmap.width, bitmap.height)

        Toolkit.blur(bitmap, blurBitmap, 4)

        drawBitmap.eraseColor(Color.TRANSPARENT)
        drawBitmapCanvas.drawBitmap(blurBitmap, null, drawBitmapRect, null)
        drawBitmapCanvas.drawRect(0f, 0f, drawBitmap.width.toFloat(), drawBitmap.height.toFloat(), paint)
    }

    override fun onBoundsChange(bounds: RectF) {
        super.onBoundsChange(bounds)
        newDrawBitmap(bounds.width().toInt(), bounds.height().toInt())
    }

    private fun checkBlurBitmap(width: Int, height: Int) {
        if (width == 0 || height == 0) {
            return
        }

        if (blurBitmap.width == width && blurBitmap.height == height) {
            return
        }
        blurBitmap.recycle()

        blurBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    private fun newDrawBitmap(width: Int, height: Int) {
        if (width == 0 || height == 0) {
            return
        }

        if (drawBitmap.width == width && drawBitmap.height == height) {
            return
        }

        drawBitmap.recycle()

        drawBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawBitmapCanvas = Canvas(drawBitmap)
        drawBitmapRect.right = width.toFloat()
        drawBitmapRect.bottom = height.toFloat()
    }

    override fun release() {
        if (!blurBitmap.isRecycled) {
            blurBitmap.recycle()
        }
        if (!drawBitmap.isRecycled) {
            drawBitmap.recycle()
        }
    }

    override fun draw(input: Canvas, drawOutput: (output: Canvas) -> Unit) {
        input.drawBitmap(drawBitmap, null, bounds, null)
    }

}