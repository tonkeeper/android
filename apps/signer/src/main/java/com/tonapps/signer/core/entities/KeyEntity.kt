package com.tonapps.signer.core.entities

import com.tonapps.blockchain.ton.extensions.publicKeyFromHex
import com.tonapps.security.hex
import com.tonapps.signer.Key
import org.json.JSONObject
import org.ton.kotlin.crypto.PublicKeyEd25519

data class KeyEntity(
    val id: Long,
    val name: String,
    val publicKey: PublicKeyEd25519,
) {

    val hex: String
        get() = hex(publicKey.key.toByteArray()).lowercase()

    constructor(json: JSONObject) : this(
        json.getLong(Key.ID),
        json.getString(Key.NAME),
        json.getString(Key.PK).publicKeyFromHex()
    )

    fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put(Key.ID, id)
        json.put(Key.NAME, name)
        json.put(Key.PK, hex)
        return json
    }
}