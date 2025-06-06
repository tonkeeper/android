package uikit.extensions

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.annotation.StringRes
import androidx.core.text.HtmlCompat

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

fun CharSequence.withDefaultBadge(
    context: Context,
    @StringRes resId: Int
): CharSequence {
    val builder = SpannableStringBuilder(this)
    builder.append(" ")
    builder.badgeDefault(context) { append(context.getString(resId)) }
    return builder
}

fun CharSequence.withGreenBadge(
    context: Context,
    @StringRes resId: Int
): CharSequence {
    val builder = SpannableStringBuilder(this)
    builder.append(" ")
    builder.badgeGreen(context) { append(context.getString(resId)) }
    return builder
}

fun CharSequence.withRedBadge(
    context: Context,
    @StringRes resId: Int
): CharSequence {
    val builder = SpannableStringBuilder(this)
    builder.append(" ")
    builder.badgeRed(context) { append(context.getString(resId)) }
    return builder
}

fun CharSequence.withBlueBadge(
    context: Context,
    @StringRes resId: Int
): CharSequence {
    val builder = SpannableStringBuilder(this)
    builder.append(" ")
    builder.badgeBlue(context) { append(context.getString(resId)) }
    return builder
}