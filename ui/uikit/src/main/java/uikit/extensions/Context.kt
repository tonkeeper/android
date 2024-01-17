package uikit.extensions

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.drawable.RippleDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AnimationUtils
import androidx.annotation.AnimRes
import androidx.annotation.ColorInt
import androidx.annotation.DimenRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.annotation.StyleableRes
import uikit.R
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

fun Context.getAnimation(@AnimRes id: Int) = AnimationUtils.loadAnimation(this, id)

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
        return resourceId.takeIf { it > 0 }?.let { resources.getDimensionPixelSize(it) } ?: 24.dp
    }


val Context.navigationBarHeight: Int
    get() {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return resourceId.takeIf { it > 0 }?.let { resources.getDimensionPixelSize(it) } ?: 48.dp
    }

val Context.locale: Locale
    get() {
        return resources.configuration.locales.get(0)
    }

fun Context.createRipple(): RippleDrawable {
    val color = getColor(R.color.backgroundHighlighted)
    return RippleDrawable(
        ColorStateList.valueOf(color),
        null,
        null
    )
}

fun Context.textWithLabel(text: String, label: String?): CharSequence {
    if (label.isNullOrEmpty()) {
        return text
    }

    val labelColor = getColor(R.color.textTertiary)
    val span = SpannableString("$text $label")
    span.setSpan(ForegroundColorSpan(labelColor), text.length, text.length + label.length + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    return span
}
