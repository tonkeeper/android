package com.tonapps.uikit.color

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

@ColorInt
fun Context.resolveColor(@AttrRes colorRef: Int): Int {
    val v = TypedValue()
    theme.resolveAttribute(colorRef, v, true)
    return v.data
}

@get:ColorInt
val Context.textPrimaryColor
    get() = resolveColor(R.attr.textPrimaryColor)

@get:ColorInt
val Context.textSecondaryColor
    get() = resolveColor(R.attr.textSecondaryColor)

@get:ColorInt
val Context.textTertiaryColor
    get() = resolveColor(R.attr.textTertiaryColor)

@get:ColorInt
val Context.textAccentColor
    get() = resolveColor(R.attr.textAccentColor)

@get:ColorInt
val Context.textPrimaryAlternateColor
    get() = resolveColor(R.attr.textPrimaryAlternateColor)


@get:ColorInt
val Context.backgroundPageColor
    get() = resolveColor(R.attr.backgroundPageColor)

@get:ColorInt
val Context.backgroundTransparentColor
    get() = resolveColor(R.attr.backgroundTransparentColor)

@get:ColorInt
val Context.backgroundContentColor
    get() = resolveColor(R.attr.backgroundContentColor)

@get:ColorInt
val Context.backgroundContentTintColor
    get() = resolveColor(R.attr.backgroundContentTintColor)

@get:ColorInt
val Context.backgroundContentAttentionColor
    get() = resolveColor(R.attr.backgroundContentAttentionColor)

@get:ColorInt
val Context.backgroundHighlightedColor
    get() = resolveColor(R.attr.backgroundHighlightedColor)

@get:ColorInt
val Context.backgroundOverlayStrongColor
    get() = resolveColor(R.attr.backgroundOverlayStrongColor)

@get:ColorInt
val Context.backgroundOverlayLightColor
    get() = resolveColor(R.attr.backgroundOverlayLightColor)

@get:ColorInt
val Context.backgroundOverlayExtraLightColor
    get() = resolveColor(R.attr.backgroundOverlayExtraLightColor)


@get:ColorInt
val Context.iconPrimaryColor
    get() = resolveColor(R.attr.iconPrimaryColor)

@get:ColorInt
val Context.iconSecondaryColor
    get() = resolveColor(R.attr.iconSecondaryColor)

@get:ColorInt
val Context.iconTertiaryColor
    get() = resolveColor(R.attr.iconTertiaryColor)

@get:ColorInt
val Context.iconPrimaryAlternateColor
    get() = resolveColor(R.attr.iconPrimaryAlternateColor)


@get:ColorInt
val Context.buttonPrimaryBackgroundColor
    get() = resolveColor(R.attr.buttonPrimaryBackgroundColor)

@get:ColorInt
val Context.buttonPrimaryBackgroundDisabledColor
    get() = resolveColor(R.attr.buttonPrimaryBackgroundDisabledColor)

@get:ColorInt
val Context.buttonPrimaryBackgroundHighlightedColor
    get() = resolveColor(R.attr.buttonPrimaryBackgroundHighlightedColor)

@get:ColorInt
val Context.buttonPrimaryForegroundColor
    get() = resolveColor(R.attr.buttonPrimaryForegroundColor)


@get:ColorInt
val Context.buttonSecondaryBackgroundColor
    get() = resolveColor(R.attr.buttonSecondaryBackgroundColor)

@get:ColorInt
val Context.buttonSecondaryBackgroundDisabledColor
    get() = resolveColor(R.attr.buttonSecondaryBackgroundDisabledColor)

@get:ColorInt
val Context.buttonSecondaryBackgroundHighlightedColor
    get() = resolveColor(R.attr.buttonSecondaryBackgroundHighlightedColor)

@get:ColorInt
val Context.buttonSecondaryForegroundColor
    get() = resolveColor(R.attr.buttonSecondaryForegroundColor)


@get:ColorInt
val Context.buttonTertiaryBackgroundColor
    get() = resolveColor(R.attr.buttonTertiaryBackgroundColor)

@get:ColorInt
val Context.buttonTertiaryBackgroundDisabledColor
    get() = resolveColor(R.attr.buttonTertiaryBackgroundDisabledColor)

@get:ColorInt
val Context.buttonTertiaryBackgroundHighlightedColor
    get() = resolveColor(R.attr.buttonTertiaryBackgroundHighlightedColor)

@get:ColorInt
val Context.buttonTertiaryForegroundColor
    get() = resolveColor(R.attr.buttonTertiaryForegroundColor)


@get:ColorInt
val Context.buttonGreenBackgroundColor
    get() = resolveColor(R.attr.buttonGreenBackgroundColor)

@get:ColorInt
val Context.buttonGreenBackgroundDisabledColor
    get() = resolveColor(R.attr.buttonGreenBackgroundDisabledColor)

@get:ColorInt
val Context.buttonGreenBackgroundHighlightedColor
    get() = resolveColor(R.attr.buttonGreenBackgroundHighlightedColor)


@get:ColorInt
val Context.fieldBackgroundColor
    get() = resolveColor(R.attr.fieldBackgroundColor)

@get:ColorInt
val Context.fieldActiveBorderColor
    get() = resolveColor(R.attr.fieldActiveBorderColor)

@get:ColorInt
val Context.fieldErrorBorderColor
    get() = resolveColor(R.attr.fieldErrorBorderColor)

@get:ColorInt
val Context.fieldErrorBackgroundColor
    get() = resolveColor(R.attr.fieldErrorBackgroundColor)


@get:ColorInt
val Context.accentBlueColor
    get() = resolveColor(R.attr.accentBlueColor)

@get:ColorInt
val Context.accentGreenColor
    get() = resolveColor(R.attr.accentGreenColor)

@get:ColorInt
val Context.accentRedColor
    get() = resolveColor(R.attr.accentRedColor)

@get:ColorInt
val Context.accentOrangeColor
    get() = resolveColor(R.attr.accentOrangeColor)

@get:ColorInt
val Context.accentPurpleColor
    get() = resolveColor(R.attr.accentPurpleColor)

@get:ColorInt
val Context.tabBarActiveIconColor
    get() = resolveColor(R.attr.tabBarActiveIconColor)

@get:ColorInt
val Context.tabBarInactiveIconColor
    get() = resolveColor(R.attr.tabBarInactiveIconColor)


@get:ColorInt
val Context.separatorCommonColor
    get() = resolveColor(R.attr.separatorCommonColor)

@get:ColorInt
val Context.separatorAlternateColor
    get() = resolveColor(R.attr.separatorAlternateColor)

@get:ColorInt
val Context.constantBlackColor
    get() = resources.getColor(R.color.constantBlack)

@get:ColorInt
val Context.constantWhiteColor
    get() = resources.getColor(R.color.constantWhite)

@get:ColorInt
val Context.constantBlueColor
    get() = resources.getColor(R.color.constantBlue)

@get:ColorInt
val Context.constantRedColor
    get() = resources.getColor(R.color.constantRed)

@get:ColorInt
val Context.constantTonColor
    get() = resources.getColor(R.color.constantTon)

val Int.stateList: ColorStateList
    get() = ColorStateList.valueOf(this)

val Int.drawable: Drawable
    get() = ColorDrawable(this)