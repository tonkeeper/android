package com.tonapps.tonkeeper.helper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Browser
import android.util.Log
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.tonapps.extensions.activity
import com.tonapps.extensions.locale
import com.tonapps.extensions.toUriOrNull
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.core.entities.WalletPurchaseMethodEntity
import com.tonapps.tonkeeper.extensions.showToast
import com.tonapps.tonkeeper.koin.installId
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.uikit.color.backgroundPageColor
import com.tonapps.uikit.color.textPrimaryColor
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.browser.entities.BrowserAppEntity
import com.tonapps.wallet.localization.Localization
import uikit.navigation.Navigation
import androidx.core.net.toUri

object BrowserHelper {

    fun BrowserAppEntity.openDApp(context: Context, wallet: WalletEntity, source: String, country: String) {
        if (useCustomTabs || useTG) {
            if (useCustomTabs) {
                open(context, url.toString())
            } else if (useTG) {
                openTG(context, url)
            }
            AnalyticsHelper.trackEventClickDApp(
                url = url.toString(),
                name = name,
                installId = context.installId,
                source = source,
                country = country
            )
        } else {
            Navigation.from(context)?.add(
                DAppScreen.newInstance(
                wallet = wallet,
                title = name,
                url = url,
                iconUrl = icon.toString(),
                source = source
            ))
        }
    }

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
        open(activity, url.toUri())
    }

    fun open(context: Context, uri: Uri) {
        context.activity?.let {
            open(it, uri)
        }
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
        intent.intent.putExtra(Browser.EXTRA_APPLICATION_ID, activity.packageName)

        try {
            intent.launchUrl(activity, uri)
        } catch (e: Throwable) {
            external(activity, uri)
        }
    }

    fun openTG(context: Context, uri: Uri) {
        context.activity?.let {
            openTG(it, uri)
        }
    }

    fun openTG(activity: Activity, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.setPackage("org.telegram.messenger")
            activity.startActivity(intent)
        } catch (e: Throwable) {
            external(activity, uri)
        }
    }

    fun external(context: Context, uri: Uri) {
        context.activity?.let {
            external(it, uri)
        }
    }

    fun external(activity: Activity, uri: Uri) {
        if (uri.scheme == "blob") {
            return
        }
        val url = uri.toString().replace("intent://", "https://").toUriOrNull() ?: return
        try {
            val intent = Intent(Intent.ACTION_VIEW, url)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        } catch (e: Throwable) {
            activity.showToast(Localization.unknown_error)
        }
    }

}