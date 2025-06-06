package com.tonapps.tonkeeper.ui.screen.browser.dapp

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.extensions.normalizeTONSites
import uikit.base.BaseArgs

data class DAppArgs(
    val title: String,
    val url: Uri,
    val source: String,
    val iconUrl: String
): BaseArgs() {

    private companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_URL = "url"
        private const val ARG_SOURCE = "source"
        private const val ARG_SEND_ANALYTICS = "send_analytics"
        private const val ARG_ICON_URL = "icon_url"
    }

    constructor(bundle: Bundle) : this(
        title = bundle.getString(ARG_TITLE)!!,
        url = bundle.getParcelableCompat(ARG_URL)!!,
        source = bundle.getString(ARG_SOURCE) ?: "",
        iconUrl = bundle.getString(ARG_ICON_URL) ?: "",
    )

    override fun toBundle(): Bundle = Bundle().apply {
        putString(ARG_TITLE, title)
        putParcelable(ARG_URL, url)
        putString(ARG_SOURCE, source)
        putString(ARG_ICON_URL, iconUrl)
    }
}