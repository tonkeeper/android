package com.tonapps.tonkeeper.core

import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.ktx.Firebase
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
}