package com.tonapps.tonkeeper.ui.screen.browser.dapp

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.extensions.normalizeTONSites
import uikit.base.BaseArgs

data class DAppArgs(
    val title: String? = null,
    val url: Uri
): BaseArgs() {

    private companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_URL = "url"
    }

    constructor(bundle: Bundle) : this(
        title = bundle.getString(ARG_TITLE),
        url = bundle.getParcelableCompat(ARG_URL)!!
    )

    override fun toBundle(): Bundle = Bundle().apply {
        putString(ARG_TITLE, title)
        putParcelable(ARG_URL, url)
    }
}