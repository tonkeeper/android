package uikit.extensions

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.ColorRes

fun Drawable.setTintRes(context: Context, @ColorRes colorRes: Int) {
    setTint(context.getColor(colorRes))
}