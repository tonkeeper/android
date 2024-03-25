package com.tonapps.tonkeeper.core.tonconnect.models

import org.json.JSONObject
import org.ton.crypto.base64

data class TCEvent(
    val from: String,
    val message: String
) {

    val body: ByteArray
        get() = base64(message)

    constructor(data: String) : this(JSONObject(data))

    constructor(json: JSONObject) : this(
        from = json.getString("from"),
        message = json.getString("message")
    )
}