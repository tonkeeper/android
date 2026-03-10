package uikit.extensions

import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.text.HtmlCompat
import com.tonapps.uikit.color.accentBlueColor

fun String.parseWords(): List<String> {
    val words = split(",", "\n", " ").map {
        it.trim()
    }.filter {
        it.isNotEmpty()
    }
    return words
}

fun String.isWords(): Boolean {
    return contains(",") || contains("\n") || contains(" ")
}

fun String.html(): Spanned {
    return HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_COMPACT)
}

fun CharSequence.spannableStringBuilder(): SpannableStringBuilder {
    if (this is SpannableStringBuilder) {
        return this
    }
    return SpannableStringBuilder(this)
}

fun CharSequence.withDefaultBadge(
    context: Context,
    @StringRes resId: Int
) = spannableStringBuilder().append(" ").badgeDefault(context) {
    append(context.getString(resId))
}

fun CharSequence.withGreenBadge(
    context: Context,
    @StringRes resId: Int
) = spannableStringBuilder().append(" ").badgeGreen(context) {
    append(context.getString(resId))
}

fun CharSequence.withRedBadge(
    context: Context,
    @StringRes resId: Int
) = spannableStringBuilder().append(" ").badgeRed(context) { append(context.getString(resId)) }

fun CharSequence.withBlueBadge(
    context: Context,
    @StringRes resId: Int
) = spannableStringBuilder().append(" ").badgeBlue(context) { append(context.getString(resId)) }

fun CharSequence.withClickable(
    context: Context,
    @StringRes resId: Int,
    @ColorInt color: Int = context.accentBlueColor,
    onClick: () -> Unit = {}
) = spannableStringBuilder().append(" ").clickable(color, onClick) { append(context.getString(resId)) }

fun CharSequence.withInterpunct() = spannableStringBuilder().append(" · ")