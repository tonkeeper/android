package com.tonapps.tonkeeper.core.signer

import android.net.Uri
import com.tonapps.blockchain.ton.extensions.publicKey
import org.ton.api.pub.PublicKeyEd25519

data class SingerArgs(
    val publicKeyEd25519: PublicKeyEd25519,
    val name: String?,
) {

    constructor(uri: Uri): this(
        publicKeyEd25519 = uri.getQueryParameter("pk")?.publicKey() ?: throw IllegalArgumentException("Public key is required"),
        name = uri.getQueryParameter("name"),
    )
}