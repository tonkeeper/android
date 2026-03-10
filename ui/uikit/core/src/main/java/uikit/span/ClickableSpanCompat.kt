package uikit.span

import android.graphics.Color
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View

class ClickableSpanCompat(
    val color: Int = Color.TRANSPARENT,
    val onClick: () -> Unit
): ClickableSpan() {

    override fun updateDrawState(ds: TextPaint) {
        ds.isUnderlineText = false
        if (color != Color.TRANSPARENT) {
            ds.color = color
        }
    }

    override fun onClick(widget: View) {
        onClick()
    }
}