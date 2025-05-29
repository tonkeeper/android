package uikit.extensions

import android.content.Context
import android.text.SpannableStringBuilder
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import androidx.core.text.inSpans
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.accentPurpleColor
import com.tonapps.uikit.color.accentRedColor
import com.tonapps.uikit.color.backgroundContentTintColor
import com.tonapps.uikit.color.textSecondaryColor
import uikit.span.BadgeSpan
import uikit.span.EllipsisSpan

inline fun SpannableStringBuilder.badge(
    context: Context,
    @ColorInt backgroundColor: Int,
    @ColorInt textColor: Int,
    @StyleRes textAppearance: Int = uikit.R.style.TextAppearance_Body4CAPS,
    builderAction: SpannableStringBuilder.() -> Unit
): SpannableStringBuilder = inSpans(BadgeSpan(
    context = context,
    backgroundColor = backgroundColor,
    textColor = textColor,
    textAppearance = textAppearance,
), builderAction = builderAction)

inline fun SpannableStringBuilder.badgeDefault(
    context: Context,
    builderAction: SpannableStringBuilder.() -> Unit
): SpannableStringBuilder = badge(
    context = context,
    backgroundColor = context.backgroundContentTintColor,
    textColor = context.textSecondaryColor,
    builderAction = builderAction,
)

inline fun SpannableStringBuilder.badgeAccentColor(
    context: Context,
    @ColorInt color: Int,
    builderAction: SpannableStringBuilder.() -> Unit
): SpannableStringBuilder = badge(
    context = context,
    backgroundColor = color.withAlpha(.16f),
    textColor = color,
    builderAction = builderAction,
)

inline fun SpannableStringBuilder.badgeOrange(
    context: Context,
    builderAction: SpannableStringBuilder.() -> Unit
) = badgeAccentColor(context, context.accentOrangeColor, builderAction)

inline fun SpannableStringBuilder.badgePurple(
    context: Context,
    builderAction: SpannableStringBuilder.() -> Unit
) = badgeAccentColor(context, context.accentPurpleColor, builderAction)

inline fun SpannableStringBuilder.badgeGreen(
    context: Context,
    builderAction: SpannableStringBuilder.() -> Unit
) = badgeAccentColor(context, context.accentGreenColor, builderAction)

inline fun SpannableStringBuilder.badgeRed(
    context: Context,
    builderAction: SpannableStringBuilder.() -> Unit
) = badgeAccentColor(context, context.accentRedColor, builderAction)

inline fun SpannableStringBuilder.ellipsis(
    maxWidth: Int,
    ellipsis: String = "…",
    builderAction: SpannableStringBuilder.() -> Unit
): SpannableStringBuilder = inSpans(EllipsisSpan(maxWidth, ellipsis), builderAction = builderAction)


