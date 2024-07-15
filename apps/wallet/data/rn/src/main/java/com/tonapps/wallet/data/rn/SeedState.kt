package com.tonapps.wallet.data.rn

import org.json.JSONObject

data class SeedState(
    val kind: String = "encrypted-scrypt-tweetnacl",
    val n: Int = SeedState.n, // scrypt "cost" parameter
    val r: Int = SeedState.r, // scrypt "block size" parameter
    val p: Int = SeedState.p, // scrypt "parallelization" parameter,
    val salt: String, // hex-encoded nonce/salt
    val ciphertext: String // hex-encoded ciphertext
) {

    companion object {
        const val n = 16384
        const val r = 8
        const val p = 1
    }

    constructor(json: JSONObject) : this(
        kind = json.getString("kind"),
        n = json.getInt("N"),
        r = json.getInt("r"),
        p = json.getInt("p"),
        salt = json.getString("salt"),
        ciphertext = json.getString("ct")
    )

    fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("kind", kind)
        json.put("N", n)
        json.put("r", r)
        json.put("p", p)
        json.put("salt", salt)
        json.put("ct", ciphertext)
        return json
    }

    val string: String
        get() = toJSON().toString()
}