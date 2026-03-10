package uikit.widget.image

import android.graphics.*
import androidx.annotation.ColorInt
import androidx.annotation.Px
import coil3.size.Size
import coil3.transform.Transformation

class BorderTransformation(
    @ColorInt private val borderColor: Int,
    @Px private val borderWidthPx: Float,
    private val asCircle: Boolean,
    @Px private val cornerRadiusPx: Float = 0f
) : Transformation() {

    private val epsilonPx = 0.4f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = borderColor
        style = Paint.Style.STROKE
        strokeWidth = borderWidthPx
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isDither = true
    }

    override val cacheKey: String = "BorderTransformation(color=$borderColor,width=$borderWidthPx,asCircle=$asCircle,corner=$cornerRadiusPx)"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val output = input.copy(input.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val half = borderWidthPx / 2f
        val left = half
        val top = half
        val right = output.width - half
        val bottom = output.height - half

        if (asCircle) {
            val cx = output.width / 2f
            val cy = output.height / 2f
            val r = ((minOf(output.width, output.height) / 2f) - half) + epsilonPx
            if (r > 0f) {
                canvas.drawCircle(cx, cy, r, paint)
            }
        } else {
            val rect = RectF(left, top, right, bottom)
            if (cornerRadiusPx > 0f) {
                canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
            } else {
                canvas.drawRect(rect, paint)
            }
        }
        return output
    }
}
