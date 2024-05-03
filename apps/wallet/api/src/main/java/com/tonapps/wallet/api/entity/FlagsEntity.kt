package com.tonapps.wallet.api.entity

import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class FlagsEntity(
    val disableSwap: Boolean,
    val disableExchangeMethods: Boolean,
    val disableDApps: Boolean,
    val disableBlur: Boolean,
    val disableLegacyBlur: Boolean,
    val disableSigner: Boolean
): Parcelable {

    constructor(json: JSONObject) : this(
        disableSwap = json.optBoolean("disable_swap", false),
        disableExchangeMethods = json.optBoolean("disable_exchange_methods", false),
        disableDApps = json.optBoolean("disable_dapps", false),
        disableBlur = json.optBoolean("disable_blur", false),
        disableLegacyBlur = json.optBoolean("disable_legacy_blur", false),
        disableSigner = json.optBoolean("disable_signer", false)
    )

    constructor() : this(
        disableSwap = false,
        disableExchangeMethods = false,
        disableDApps = false,
        disableBlur = false,
        disableLegacyBlur = false,
        disableSigner = false
    )
}