package com.tonapps.tonkeeper.ui.screen.browser.dapp

import android.net.Uri
import android.os.Bundle
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.ui.screen.browser.analytics.DappOpenAnalytics
import uikit.base.BaseArgs

data class DAppArgs(
    val title: String,
    val url: Uri,
    val source: String,
    val iconUrl: String,
    val forceConnect: Boolean,
    val analytics: DappOpenAnalytics? = null,
    val utm: UtmParameters = UtmParameters.Empty,
    val type: DAppType = DAppType.Browser,
): BaseArgs() {

    constructor(bundle: Bundle) : this(
        title = bundle.getString(ARG_TITLE)!!,
        url = bundle.getParcelableCompat<Uri>(ARG_URL)!!,
        source = bundle.getString(ARG_SOURCE) ?: "",
        iconUrl = bundle.getString(ARG_ICON_URL) ?: "",
        forceConnect = bundle.getBoolean(ARG_FORCE_CONNECT),
        analytics = bundle.getParcelableCompat<DappOpenAnalytics>(ARG_ANALYTICS),
        utm = bundle.getParcelableCompat<UtmParameters>(ARG_UTM) ?: UtmParameters.Empty,
        type = DAppType.entries.firstOrNull { it.name == bundle.getString(ARG_TYPE) } ?: DAppType.Browser,
    )

    override fun toBundle(): Bundle = Bundle().apply {
        putString(ARG_TITLE, title)
        putParcelable(ARG_URL, url)
        putString(ARG_SOURCE, source)
        putString(ARG_ICON_URL, iconUrl)
        putBoolean(ARG_FORCE_CONNECT, forceConnect)
        putParcelable(ARG_ANALYTICS, analytics)
        putParcelable(ARG_UTM, utm)
        putString(ARG_TYPE, type.name)
    }

    private companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_URL = "url"
        private const val ARG_SOURCE = "source"
        private const val ARG_ICON_URL = "icon_url"
        private const val ARG_FORCE_CONNECT = "force_connect"
        private const val ARG_ANALYTICS = "analytics"
        private const val ARG_UTM = "utm"
        private const val ARG_TYPE = "type"
    }
}
