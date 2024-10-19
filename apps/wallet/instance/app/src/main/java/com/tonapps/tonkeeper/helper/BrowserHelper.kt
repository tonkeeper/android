package com.tonapps.tonkeeper.helper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.tonapps.extensions.activity
import com.tonapps.extensions.locale
import com.tonapps.tonkeeper.core.entities.WalletPurchaseMethodEntity
import com.tonapps.tonkeeper.extensions.showToast
import com.tonapps.uikit.color.backgroundPageColor
import com.tonapps.uikit.color.textPrimaryColor
import com.tonapps.wallet.localization.Localization

object BrowserHelper {

    fun openPurchase(context: Context, method: WalletPurchaseMethodEntity) {
        context.activity?.let {
            open(it, method.uri)
        }
    }

    fun open(context: Context, url: String) {
        context.activity?.let {
            open(it, url)
        }
    }

    fun open(activity: Activity, url: String) {
        open(activity, Uri.parse(url))
    }

    fun open(activity: Activity, uri: Uri) {
        if (uri.scheme != "http" && uri.scheme != "https") {
            external(activity, uri)
            return
        }

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
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .setShareIdentityEnabled(false)
            .setDownloadButtonEnabled(false)
            .setCloseButtonPosition(CustomTabsIntent.CLOSE_BUTTON_POSITION_END)
            .setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_LIGHT, colorSchemeParams)
            .setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_DARK, colorSchemeParams)
            .build()

        try {
            intent.launchUrl(activity, uri)
        } catch (e: Throwable) {
            external(activity, uri)
        }
    }

    private fun external(activity: Activity, uri: Uri) {
        if (uri.scheme == "blob") {
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        } catch (e: Throwable) {
            activity.showToast(Localization.unknown_error)
        }
    }

}