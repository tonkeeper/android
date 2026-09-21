package uikit.span

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.text.style.ReplacementSpan
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import com.tonapps.uikit.color.backgroundContentTintColor
import com.tonapps.uikit.color.iconSecondaryColor
import uikit.extensions.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// View-world twin of the Compose MoonCutRow: a row of circular icons where each icon overlaps
// the next one and punches a transparent ring out of it (earlier items on top). Optionally ends
// with a "more" badge — a neutral circle with three dots.
class CutIconsSpan(
    context: Context,
    @DrawableRes iconResIds: List<Int>,
    private val showMore: Boolean = false,
) : ReplacementSpan() {

    private val size = 18f.dp
    private val overlap = 2f.dp
    private val cutPadding = 2f.dp

    private val icons: List<Drawable> = iconResIds.mapNotNull {
        AppCompatResources.getDrawable(context, it)
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val moreBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.backgroundContentTintColor
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.iconSecondaryColor
    }

    private val count: Int = icons.size + if (showMore) 1 else 0

    private val width: Float = if (count == 0) 0f else size + (count - 1) * (size - overlap)

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        if (fm != null) {
            // Grow the line if the icon row is taller than the surrounding text.
            val center = (fm.ascent + fm.descent) / 2f
            val half = size / 2f
            fm.ascent = min(fm.ascent, (center - half).roundToInt())
            fm.descent = max(fm.descent, (center + half).roundToInt())
            fm.top = min(fm.top, fm.ascent)
            fm.bottom = max(fm.bottom, fm.descent)
        }
        return width.roundToInt()
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
        if (count == 0) {
            return
        }

        val centerY = (top + bottom) / 2f
        val radius = size / 2f
        val step = size - overlap

        // The CLEAR punches must be composited in an offscreen layer, otherwise they would cut
        // through the view background as well.
        val layer = canvas.saveLayer(
            x,
            centerY - radius - cutPadding,
            x + width,
            centerY + radius + cutPadding,
            null,
        )

        // Draw right to left: every icon except the last punches a ring out of the icon it overlaps.
        for (index in count - 1 downTo 0) {
            val centerX = x + radius + index * step
            if (index < count - 1) {
                canvas.drawCircle(centerX, centerY, radius + cutPadding, clearPaint)
            }
            if (showMore && index == icons.size) {
                drawMore(canvas, centerX, centerY, radius)
            } else {
                val icon = icons[index]
                icon.setBounds(
                    (centerX - radius).roundToInt(),
                    (centerY - radius).roundToInt(),
                    (centerX + radius).roundToInt(),
                    (centerY + radius).roundToInt(),
                )
                icon.draw(canvas)
            }
        }

        canvas.restoreToCount(layer)
    }

    private fun drawMore(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        canvas.drawCircle(centerX, centerY, radius, moreBackgroundPaint)
        val gap = 3.5f.dp
        val dotRadius = 1.25f.dp
        for (i in -1..1) {
            canvas.drawCircle(centerX + i * gap, centerY, dotRadius, dotPaint)
        }
    }
}
