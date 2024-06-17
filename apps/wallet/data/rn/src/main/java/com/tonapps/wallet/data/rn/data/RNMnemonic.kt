package com.tonapps.wallet.data.rn.data

import org.json.JSONObject

data class RNMnemonic(val identifier: String, val mnemonic: String) {

    constructor(json: JSONObject) : this(
        json.getString("identifier"),
        json.getString("mnemonic")
    )
}