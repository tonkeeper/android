package com.tonapps.tonkeeper.core

import android.app.Activity
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.tonapps.extensions.locale
import com.tonapps.uikit.color.backgroundPageColor
import com.tonapps.uikit.color.textPrimaryColor

object CustomTabsHelper {

    fun open(activity: Activity, uri: Uri) {
        val barBackgroundColor = activity.backgroundPageColor
        val textColor = activity.textPrimaryColor

        val colorSchemeParams = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(barBackgroundColor)
            .setNavigationBarColor(barBackgroundColor)
            .setNavigationBarDividerColor(barBackgroundColor)
            .setSecondaryToolbarColor(textColor)
            .build()

        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setTranslateLocale(activity.locale)
            .setBookmarksButtonEnabled(false)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setShareIdentityEnabled(false)
            .setDownloadButtonEnabled(false)
            .setCloseButtonPosition(CustomTabsIntent.CLOSE_BUTTON_POSITION_END)
            .setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_LIGHT, colorSchemeParams)
            .setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_DARK, colorSchemeParams)
            .build()
        intent.launchUrl(activity, uri)
    }
}