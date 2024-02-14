package com.tonapps.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import com.tonapps.qr.R
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

    private companion object {
        private const val CHUNK_SIZE = 256
        private const val NEXT_DELAY = 100L
    }

    var withCutout: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                requestUpdateBitmap()
            }
        }

    private var chunkIndex = -1
        set(value) {
            if (field != value) {
                field = value
                requestUpdateBitmap()
            }
        }

    private var chunks: List<String> = emptyList()
    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private val nextRunnable = Runnable { next() }

    private var bitmap: Bitmap? = null
        set(value) {
            field?.recycle()
            field = value
        }

    private val color: Int

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.QRView, 0, 0).apply {
            try {
                color = getColor(R.styleable.QRView_android_color, Color.WHITE)
            } finally {
                recycle()
            }
        }
    }

    fun setContent(content: String) {
        val diff = content.length - CHUNK_SIZE
        chunks = if (diff > 20) {
            content.chunked(CHUNK_SIZE)
        } else {
            listOf(content)
        }

        chunkIndex = -1
        next()
    }

    private fun next() {
        if (chunks.isEmpty()) {
            return
        }

        chunkIndex = (chunkIndex + 1) % chunks.size
        if (chunks.size == 1) {
            return
        }

        startAnimation()
    }

    private fun startAnimation() {
        if (isShown && isAttachedToWindow && chunks.size > 1) {
            postDelayed(nextRunnable, NEXT_DELAY)
        }
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
            val content = chunks.getOrNull(chunkIndex) ?: return
            updateBitmap(width - (paddingLeft + paddingRight), content)
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
        builder.setColor(color)
        builder.setWithCutout(withCutout)
        builder.build()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(nextRunnable)
        scope.cancel()
        bitmap = null
    }

    override fun hasOverlappingRendering() = false
}