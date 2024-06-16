package com.tonapps.wallet.data.rn.data

import org.json.JSONObject

data class RNWallets(
    val wallets: List<RNWallet>,
    val selectedIdentifier: String,
    val biometryEnabled: Boolean,
    val lockScreenEnabled: Boolean
): RNData() {

    companion object {
        val empty = RNWallets(
            wallets = emptyList(),
            selectedIdentifier = "",
            biometryEnabled = false,
            lockScreenEnabled = false,
        )
    }

    constructor(json: JSONObject) : this(
        wallets = RNWallet.fromJSONArray(json.optJSONArray("wallets")),
        selectedIdentifier = json.optString("selectedIdentifier", ""),
        biometryEnabled = json.optBoolean("biometryEnabled", false),
        lockScreenEnabled = json.optBoolean("lockScreenEnabled")
    )


    override fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("selectedIdentifier", selectedIdentifier)
        json.put("biometryEnabled", biometryEnabled)
        json.put("lockScreenEnabled", lockScreenEnabled)
        json.put("wallets", RNWallet.toJSONArray(wallets))
        return json
    }

}