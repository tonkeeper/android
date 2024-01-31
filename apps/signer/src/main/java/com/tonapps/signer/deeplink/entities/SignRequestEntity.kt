package com.tonapps.signer.deeplink.entities

import android.net.Uri
import org.ton.api.pub.PublicKeyEd25519
import org.ton.crypto.base64

data class SignRequestEntity(val uri: Uri) {

    companion object {

        fun safe(uri: Uri): SignRequestEntity? {
            return try {
                SignRequestEntity(uri)
            } catch (e: Throwable) {
                null
            }
        }
    }

    val body: String = uri.getQueryParameter("body") ?: throw IllegalArgumentException("Body is not specified")

    val publicKey: PublicKeyEd25519 = uri.getQueryParameter("pk")?.let {
        PublicKeyEd25519(base64(it))
    } ?: throw IllegalArgumentException("Public key is not specified")

}