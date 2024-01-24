package com.tonkeeper.core.singer

import android.net.Uri
import org.ton.api.pub.PublicKeyEd25519
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.crypto.base64
import ton.extensions.base64

object SignerApp {

    fun createSignUri(cell: Cell, publicKey: PublicKeyEd25519): Uri {
        val boc = BagOfCells(cell)
        return createSignUri(boc, publicKey)
    }

    fun createSignUri(boc: BagOfCells, publicKey: PublicKeyEd25519): Uri {
        val body = base64(boc.toByteArray())
        return createSignUri(body, publicKey)
    }

    fun createSignUri(boc: String, publicKey: PublicKeyEd25519): Uri {
        val builder = Uri.Builder()
        builder.scheme("tonsign")
        builder.authority("")
        builder.appendQueryParameter("pk", publicKey.base64())
        builder.appendQueryParameter("body", boc)
        return builder.build()
    }
}