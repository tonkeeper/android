package uikit.span

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan

class EllipsisSpan(
    private val maxWidth: Int,
    private val ellipsis: String = "…"
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        text ?: return 0
        val textWidth = paint.measureText(text, start, end)
        return minOf(maxWidth, textWidth.toInt())
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        text ?: return

        val textWidth = paint.measureText(text, start, end)
        if (textWidth <= maxWidth) {
            canvas.drawText(text, start, end, x, y.toFloat(), paint)
            return
        }

        val ellipsisWidth = paint.measureText(ellipsis)
        var truncatedWidth = maxWidth - ellipsisWidth
        var truncatedEnd = start

        // Find the last character that fits
        while (truncatedEnd < end) {
            val nextChar = text[truncatedEnd]
            val charWidth = paint.measureText(nextChar.toString())
            if (truncatedWidth - charWidth < 0) break
            truncatedWidth -= charWidth
            truncatedEnd++
        }

        // Draw truncated text with ellipsis
        canvas.drawText(text, start, truncatedEnd, x, y.toFloat(), paint)
        canvas.drawText(ellipsis, 0, ellipsis.length, x + truncatedWidth, y.toFloat(), paint)
    }
}