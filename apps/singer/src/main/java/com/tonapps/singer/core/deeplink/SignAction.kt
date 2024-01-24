package com.tonapps.singer.core.deeplink

import android.net.Uri
import com.tonapps.singer.core.Network
import core.extensions.getQuery
import org.ton.api.pub.PublicKeyEd25519
import org.ton.crypto.base64

data class SignAction(val uri: Uri) {

    companion object {

        fun safe(uri: Uri): SignAction? {
            return try {
                SignAction(uri)
            } catch (e: Throwable) {
                null
            }
        }
    }

    val body: String = uri.getQuery("body") ?: throw IllegalArgumentException("Body is not specified")

    val publicKey: PublicKeyEd25519 = uri.getQuery("pk")?.let {
        PublicKeyEd25519(base64(it))
    } ?: throw IllegalArgumentException("Public key is not specified")

    val network: Network = uri.getQuery("network")?.let {
        Network.valueOf(it)
    } ?: Network.TON

}