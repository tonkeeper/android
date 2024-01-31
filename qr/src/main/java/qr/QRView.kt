package qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QRView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    var withCutout: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                requestUpdateBitmap()
            }
        }

    var content: String? = null
        set(value) {
            if (field != value) {
                field = value
                requestUpdateBitmap()
            }
        }

    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    private var bitmap: Bitmap? = null
        set(value) {
            field?.recycle()
            field = value
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val qr = bitmap ?: return

        val left = (width - qr.width) / 2f
        val top = (height - qr.height) / 2f
        canvas.drawBitmap(qr, left, top, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        requestUpdateBitmap()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }

    private fun requestUpdateBitmap() {
        if (width != 0) {
            content?.let { updateBitmap(width - (paddingLeft + paddingRight), it) }
        }
    }

    private fun updateBitmap(size: Int, content: String) {
        scope.launch {
            bitmap = generateBitmap(size, content)
            invalidate()
        }
    }

    private suspend fun generateBitmap(
        size: Int,
        content: String
    ): Bitmap = withContext(Dispatchers.IO) {
        val builder = QRBuilder(content, size)
        builder.setWithCutout(withCutout)
        builder.build()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
        bitmap = null
    }

    override fun hasOverlappingRendering() = false
}