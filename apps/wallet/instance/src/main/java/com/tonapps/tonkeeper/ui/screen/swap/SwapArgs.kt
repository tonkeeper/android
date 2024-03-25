package com.tonapps.tonkeeper.ui.screen.swap

import android.net.Uri
import android.os.Bundle
import com.tonapps.extensions.getParcelableCompat

data class SwapArgs(
    val uri: Uri,
    val address: String
) {

    private companion object {
        private const val ARG_URI = "uri"
        private const val ARG_ADDRESS = "address"
    }

    constructor(bundle: Bundle) : this(
        uri = bundle.getParcelableCompat(ARG_URI)!!,
        address = bundle.getString(ARG_ADDRESS)!!
    )

    fun toBundle(): Bundle = Bundle().apply {
        putParcelable(ARG_URI, uri)
        putString(ARG_ADDRESS, address)
    }
}