package uikit.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.annotation.ColorInt
import com.tonapps.uikit.color.GlassColorMath
import com.tonapps.uikit.color.GlassSpec
import com.tonapps.uikit.color.GlassStyle
import com.tonapps.uikit.color.backgroundContentColor
import com.tonapps.uikit.color.backgroundPageColor
import com.tonapps.uikit.color.buttonPrimaryBackgroundColor
import com.tonapps.uikit.color.glassBorderColor
import com.tonapps.uikit.color.glassRimBottomColor
import com.tonapps.uikit.color.glassRimTopColor
import uikit.base.BaseDrawable
import uikit.extensions.dp
import uikit.extensions.withAlpha

class GlassDrawable(
    context: Context,
    private val style: GlassStyle,
    @ColorInt tint: Int? = null,
) : BaseDrawable() {

    private val tintColor = tint ?: when (style) {
        GlassStyle.Regular -> context.backgroundContentColor
        GlassStyle.Prominent -> context.buttonPrimaryBackgroundColor
    }
    private val pageColor = context.backgroundPageColor
    private val rimTopColor = context.glassRimTopColor
    private val rimBottomColor = context.glassRimBottomColor
    private val borderColor = context.glassBorderColor
    private val isBorderVisible = style == GlassStyle.Regular && Color.alpha(borderColor) > 0

    private val borderWidth = GlassSpec.BORDER_WIDTH_DP.dp
    private val rimWidth = GlassSpec.RIM_WIDTH_DP.dp

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = borderColor
        strokeWidth = borderWidth
        style = Paint.Style.STROKE
    }

    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = rimWidth
        style = Paint.Style.STROKE
    }

    private val boundF = RectF()
    private var cornerRadius = 0f

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        boundF.set(bounds)
        cornerRadius = minOf(boundF.width(), boundF.height()) / 2f
        fillPaint.shader = LinearGradient(
            0f,
            boundF.top,
            0f,
            boundF.bottom,
            GlassColorMath.fillStops(style, tintColor, pageColor),
            GlassColorMath.fillPositions(),
            Shader.TileMode.CLAMP
        )
        rimPaint.shader = LinearGradient(
            0f,
            boundF.top,
            0f,
            boundF.bottom,
            intArrayOf(
                rimTopColor,
                rimTopColor.withAlpha(0),
                rimBottomColor.withAlpha(0),
                rimBottomColor
            ),
            GlassSpec.rimPositions(),
            Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        fillPaint.alpha = alpha
        canvas.drawRoundRect(boundF, cornerRadius, cornerRadius, fillPaint)
        val minSide = minOf(boundF.width(), boundF.height())
        if (isBorderVisible && minSide >= borderWidth) {
            borderPaint.alpha = Color.alpha(borderColor) * alpha / OPAQUE_ALPHA
            drawInsetStroke(canvas, borderWidth, borderPaint)
        }
        if (minSide >= rimWidth) {
            rimPaint.alpha = alpha
            drawInsetStroke(canvas, rimWidth, rimPaint)
        }
    }

    private fun drawInsetStroke(canvas: Canvas, strokeWidth: Float, paint: Paint) {
        val inset = strokeWidth / 2f
        canvas.drawRoundRect(
            boundF.left + inset,
            boundF.top + inset,
            boundF.right - inset,
            boundF.bottom - inset,
            cornerRadius,
            cornerRadius,
            paint
        )
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, cornerRadius)
        outline.alpha = 1f
    }

    private companion object {
        const val OPAQUE_ALPHA = 255
    }
}
