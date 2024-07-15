package com.tonapps.signer.core.entities

import com.tonapps.security.hex
import com.tonapps.signer.Key
import org.json.JSONObject
import org.ton.api.pub.PublicKeyEd25519

data class KeyEntity(
    val id: Long,
    val name: String,
    val publicKey: PublicKeyEd25519,
) {

    val hex: String
        get() = publicKey.key.hex().lowercase()

    constructor(json: JSONObject) : this(
        json.getLong(Key.ID),
        json.getString(Key.NAME),
        PublicKeyEd25519(json.getString(Key.PK).hex())
    )

    fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put(Key.ID, id)
        json.put(Key.NAME, name)
        json.put(Key.PK, hex)
        return json
    }
}