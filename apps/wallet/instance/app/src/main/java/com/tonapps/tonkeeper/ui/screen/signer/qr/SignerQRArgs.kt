package com.tonapps.tonkeeper.ui.screen.signer.qr

import android.os.Bundle
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.ton.extensions.parseCell
import com.tonapps.blockchain.ton.extensions.publicKey
import org.ton.api.pub.PublicKeyEd25519
import org.ton.cell.Cell
import uikit.base.BaseArgs

data class SignerQRArgs(
    val publicKey: PublicKeyEd25519,
    val unsignedBody: Cell,
    val label: String
): BaseArgs() {

    private companion object {
        private const val ARG_PK = "pk"
        private const val ARG_BODY = "body"
        private const val ARG_LABEL = "label"
    }

    constructor(bundle: Bundle) : this(
        publicKey = bundle.getString(ARG_PK)!!.publicKey(),
        unsignedBody = bundle.getString(ARG_BODY)!!.parseCell(),
        label = bundle.getString(ARG_LABEL)!!
    )

    override fun toBundle(): Bundle {
        val bundle = Bundle()
        bundle.putString(ARG_PK, publicKey.hex())
        bundle.putString(ARG_BODY, unsignedBody.hex())
        bundle.putString(ARG_LABEL, label)
        return bundle
    }
}