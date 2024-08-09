package com.tonapps.tonkeeper.ui.screen.browser.dapp

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import com.tonapps.tonkeeper.extensions.normalizeTONSites
import uikit.base.BaseArgs

data class DAppArgs(
    val title: String? = null,
    val host: String?,
    val url: String
): BaseArgs() {

    private companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_HOST = "host"
        private const val ARG_URL = "url"
    }

    constructor(bundle: Bundle) : this(
        title = bundle.getString(ARG_TITLE),
        host = bundle.getString(ARG_HOST),
        url = bundle.getString(ARG_URL)!!.normalizeTONSites()
    )

    constructor(title: String? = null, host: String?, uri: Uri) : this(
        title = title,
        host = host,
        url = uri.toString()
    )

    override fun toBundle(): Bundle = Bundle().apply {
        putString("title", title)
        putString("host", host)
        putString("url", url)
    }
}