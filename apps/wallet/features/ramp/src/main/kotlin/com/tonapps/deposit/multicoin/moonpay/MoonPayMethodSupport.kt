package com.tonapps.deposit.multicoin.moonpay

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.tonapps.log.L

// Which MoonPay methods the app can pay with. Apple Pay is iOS only; Google Pay runs in MoonPay's WebView through
// the Payment Request API, which Google allows only for an approved app (the flag) on a recent enough device.
class MoonPayMethodSupport(
    private val googlePayEnabled: () -> Boolean,
    private val deviceSupportsGooglePay: () -> Boolean,
) {

    fun supports(type: String): Boolean {
        return when (type) {
            MOONPAY_APPLE_PAY_TYPE -> false
            MOONPAY_GOOGLE_PAY_TYPE -> googlePayEnabled() && deviceSupportsGooglePay()
            else -> true
        }
    }

    companion object {

        // Google's requirements for Google Pay in a WebView: WebView 137+ and Google Play services 25.18.30+.
        private const val MIN_WEBVIEW_MAJOR = 137
        private const val MIN_PLAY_SERVICES_VERSION = 251_830_000L
        private const val PLAY_SERVICES_PACKAGE = "com.google.android.gms"

        fun deviceSupportsGooglePay(context: Context): Boolean {
            return try {
                WebViewFeature.isFeatureSupported(WebViewFeature.PAYMENT_REQUEST) &&
                    webViewMajor(context) >= MIN_WEBVIEW_MAJOR &&
                    playServicesVersion(context) >= MIN_PLAY_SERVICES_VERSION
            } catch (e: Exception) {
                L.w("Google Pay support check failed: ${e.message}")
                false
            }
        }

        private fun webViewMajor(context: Context): Int {
            val version = WebViewCompat.getCurrentWebViewPackage(context)?.versionName ?: return 0
            return version.substringBefore('.').toIntOrNull() ?: 0
        }

        private fun playServicesVersion(context: Context): Long {
            return try {
                PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(PLAY_SERVICES_PACKAGE, 0))
            } catch (_: PackageManager.NameNotFoundException) {
                0L
            }
        }
    }
}

const val MOONPAY_GOOGLE_PAY_TYPE = "google_pay"
const val MOONPAY_APPLE_PAY_TYPE = "apple_pay"
