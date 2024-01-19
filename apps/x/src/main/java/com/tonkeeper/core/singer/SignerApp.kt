package com.tonkeeper.core.singer

import android.net.Uri
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.crypto.base64

object SignerApp {

    fun createSignUri(cell: Cell): Uri {
        val boc = BagOfCells(cell)
        return createSignUri(boc)
    }

    fun createSignUri(boc: BagOfCells): Uri {
        val body = base64(boc.toByteArray())
        return createSignUri(body)
    }

    fun createSignUri(boc: String): Uri {
        val builder = Uri.Builder()
        builder.scheme("tonsign")
        builder.authority("")
        builder.appendQueryParameter("body", boc)
        return builder.build()
    }
}