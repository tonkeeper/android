package com.tonapps.tonkeeper.extensions

import android.content.Context
import android.text.SpannableStringBuilder
import androidx.annotation.StringRes
import uikit.extensions.badgeDefault
import uikit.extensions.badgeGreen
import uikit.extensions.badgeOrange
import uikit.extensions.badgePurple

fun SpannableStringBuilder.badgeDefault(
    context: Context,
    @StringRes resId: Int
): SpannableStringBuilder {
    append(" ")
    return badgeDefault(context) {
        append(context.getString(resId).uppercase())
    }
}

fun SpannableStringBuilder.badgeGreen(
    context: Context,
    @StringRes resId: Int
): SpannableStringBuilder {
    append(" ")
    return badgeGreen(context) {
        append(context.getString(resId).uppercase())
    }
}

fun SpannableStringBuilder.badgePurple(
    context: Context,
    @StringRes resId: Int
): SpannableStringBuilder {
    append(" ")
    return badgePurple(context) {
        append(context.getString(resId).uppercase())
    }
}

fun SpannableStringBuilder.badgeOrange(
    context: Context,
    @StringRes resId: Int
): SpannableStringBuilder {
    append(" ")
    return badgeOrange(context) {
        append(context.getString(resId).uppercase())
    }
}