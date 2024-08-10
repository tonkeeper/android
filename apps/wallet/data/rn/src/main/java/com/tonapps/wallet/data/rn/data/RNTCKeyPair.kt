package com.tonapps.wallet.data.rn.data

import com.tonapps.security.CryptoBox
import com.tonapps.security.hex
import org.json.JSONObject

data class RNTCKeyPair(
    val publicKey: String,
    val secretKey: String
) {

    constructor(json: JSONObject) : this(
        json.getString("publicKey"),
        json.getString("secretKey")
    )

    constructor(keyPair: CryptoBox.KeyPair) : this(
        publicKey = hex(keyPair.publicKey),
        secretKey = hex(keyPair.privateKey)
    )

    fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("publicKey", publicKey)
        json.put("secretKey", secretKey)
        return json
    }
}