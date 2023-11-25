package uikit.extensions

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.TypedArray
import android.text.SpannableString
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.annotation.ColorInt
import androidx.annotation.DimenRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.annotation.StyleableRes
import java.util.Locale

fun Context.inflate(
    @LayoutRes layoutId: Int,
    root: ViewGroup? = null,
    attachToRoot: Boolean = false
): View {
    return LayoutInflater.from(this).inflate(layoutId, root, attachToRoot)
}

fun Context.getDimensionPixelSize(@DimenRes id: Int): Int {
    return resources.getDimensionPixelSize(id)
}

fun Context.getDimension(@DimenRes id: Int): Float {
    return resources.getDimension(id)
}

fun Context.getSpannable(@StringRes id: Int): SpannableString {
    return getText(id).processAnnotation(this)
}

@SuppressLint("DiscouragedApi")
@ColorInt
fun Context.getColorByIdentifier(name: String): Int {
    return resources.getIdentifier(name, "color", packageName).let {
        getColor(it)
    }
}

val Context.activity: Activity?
    get() {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }

val Context.window: Window?
    get() = activity?.window

fun Context.useAttributes(
    set: AttributeSet?,
    @StyleableRes attrs: IntArray,
    block: (TypedArray) -> Unit) {
    theme.obtainStyledAttributes(set, attrs, 0, 0).apply {
        try {
            block(this)
        } finally {
            recycle()
        }
    }
}

val Context.statusBarHeight: Int
    get() {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return resourceId.takeIf { it > 0 }?.let { resources.getDimensionPixelSize(it) } ?: 0
    }

val Context.locale: Locale
    get() {
        return resources.configuration.locales.get(0)
    }

