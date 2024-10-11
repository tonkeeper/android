package com.tonapps.tonkeeper.manager.widget

import android.content.SharedPreferences
import android.os.Parcelable
import com.tonapps.extensions.string
import kotlinx.parcelize.Parcelize

abstract class WidgetParams(open val walletId: String): Parcelable {

    private companion object {
        private const val KEY_WALLET_ID = "wallet_id"
        private const val KEY_JETTON_ADDRESS = "jetton_address"

        private fun key(prefix: String, key: String) = "${prefix}.${key}"
    }

    open val isEmpty: Boolean
        get() = walletId.isEmpty()

    abstract fun save(prefix: String, prefs: SharedPreferences)

    @Parcelize
    data class Rate(
        override val walletId: String,
        val jettonAddress: String
    ): WidgetParams(walletId) {

        constructor(prefix: String, prefs: SharedPreferences) : this(
            walletId = prefs.getString(key(prefix, KEY_WALLET_ID), "")!!,
            jettonAddress = prefs.string(key(prefix, KEY_JETTON_ADDRESS)) ?: "TON"
        )

        override fun save(prefix: String, prefs: SharedPreferences) {
            prefs.edit()
                .putString(key(prefix, KEY_WALLET_ID), walletId)
                .putString(key(prefix, KEY_JETTON_ADDRESS), jettonAddress)
                .apply()
        }

    }

    @Parcelize
    data class Balance(
        override val walletId: String,
        val jettonAddress: String? = null
    ): WidgetParams(walletId) {

        constructor(prefix: String, prefs: SharedPreferences) : this(
            walletId = prefs.getString(key(prefix, KEY_WALLET_ID), "")!!,
            jettonAddress = prefs.getString(key(prefix, KEY_JETTON_ADDRESS), null)
        )

        override fun save(prefix: String, prefs: SharedPreferences) {
            prefs.edit()
                .putString(key(prefix, KEY_WALLET_ID), walletId)
                .putString(key(prefix, KEY_JETTON_ADDRESS), jettonAddress)
                .apply()
        }
    }
}