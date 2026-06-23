package com.tonapps.tonkeeper.core

import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import com.tonapps.wallet.data.settings.SafeModeState

object FirebaseHelper {

    fun secureModeEnabled(state: SafeModeState) {
        Firebase.analytics.logEvent("secure_mode_enabled") {
            param("enabled", state.toString())
        }
    }

    fun trc20Enabled(enabled: Boolean) {
        Firebase.analytics.logEvent("trc20_enabled") {
            param("enabled", enabled.toString())
        }
    }

    fun searchEngine(value: String) {
        Firebase.analytics.logEvent("search_engine") {
            param("engine", value)
        }
    }

    fun setTitleEmoji(emoji: String) {
        Firebase.analytics.logEvent("set_title_emoji") {
            param("emoji", emoji)
        }
    }

    fun manifestFetchFailed(kind: String, url: String?) {
        Firebase.analytics.logEvent("manifest_fetch_failed") {
            param("kind", kind)
            if (url != null) param("url", url)
        }
    }

    fun manifestFetchRetry(url: String?) {
        Firebase.analytics.logEvent("manifest_fetch_retry") {
            if (url != null) param("url", url)
        }
    }
}