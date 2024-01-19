package com.tonapps.singer.core

import android.net.Uri
import org.ton.api.pub.PublicKeyEd25519

data class KeyEntity(
    val id: Long,
    val name: String,
    val publicKey: PublicKeyEd25519,
) {

    val hex: String
        get() = publicKey.key.hex()

    val publicKeyBase64: String
        get() = publicKey.key.base64()

    val exportUri: Uri by lazy {
        val builder = Uri.Builder()
        builder.scheme("tonkeeper")
        builder.authority("signer")
        builder.appendQueryParameter("k", publicKeyBase64)
        builder.appendQueryParameter("n", name)
        builder.appendQueryParameter("i", id.toString())
        builder.build()
    }
}