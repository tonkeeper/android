package uikit.widget.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.text.TextPaint
import android.util.Log
import android.view.Gravity
import androidx.core.content.res.ResourcesCompat
import com.tonapps.uikit.color.textSecondaryColor
import uikit.drawable.TextDrawable
import uikit.extensions.dp
import uikit.extensions.getDimension
import uikit.extensions.range
import uikit.extensions.sp

internal class HintDrawable(private val context: Context): TextDrawable() {

    private companion object {
        const val EXPANDED_SCALE = 1f
        const val COLLAPSED_SCALE = .75f
        val COLLAPSED_Y = 10f.dp
    }

    private val horizontalOffset = context.getDimension(uikit.R.dimen.offsetMedium)
    private var matrix: Matrix? = null

    init {
        paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.textSecondaryColor
            textSize = 16f.sp
            typeface = ResourcesCompat.getFont(context, uikit.R.font.montserrat_medium)
        }
        singleLine = true
        gravity = Gravity.CENTER_VERTICAL
    }

    override fun draw(canvas: Canvas) {
        if (matrix == null) {
            super.draw(canvas)
        } else {
            val staticLayout = requireStaticLayout()
            canvas.concat(matrix)
            staticLayout.draw(canvas)
        }
    }

    fun setProgress(progress: Float) {
        val scale = progress.range(COLLAPSED_SCALE, EXPANDED_SCALE)
        val y = progress.range(COLLAPSED_Y, translateY)

        if (matrix == null) {
            matrix = Matrix()
        } else {
            matrix?.reset()
        }
        matrix?.apply {
            preScale(scale, scale)
            postTranslate(horizontalOffset, y)
        }
    }
}