package uikit.extensions

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextPaint
import androidx.annotation.StyleRes
import com.google.android.material.resources.TextAppearance

@SuppressLint("RestrictedApi", "VisibleForTests")
fun TextPaint.setTextAppearance(
    context: Context,
    @StyleRes resId: Int
) {
    val textAppearance = TextAppearance(context, resId)
    textSize = textAppearance.textSize
    typeface = textAppearance.getFont(context)
}