package com.tonapps.dapp.component

import android.content.Context
import android.view.View
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.core.components.chainImageResourceUrl
import com.tonapps.core.components.imageResourceUrl
import uikit.widget.AsyncImageView

fun Context.browserChainIconUrl(chainId: String): String? {
    val chain = Chain.all.find { it.network.type.id.equals(chainId, ignoreCase = true) }
        ?: return null
    return chain.coin.chainImageResourceUrl(this)
}

fun AsyncImageView.bindBrowserNetworkIcon(chains: List<String>) {
    val chain = chains
        .mapNotNull { id -> Chain.all.find { it.network.type.id.equals(id, ignoreCase = true) } }
        .distinctBy { it.network.type }
        .singleOrNull()
    val iconUrl = chain?.coin?.chainImageResourceUrl(context)
    if (iconUrl == null) {
        visibility = View.GONE
        clear(null)
        return
    }
    visibility = View.VISIBLE
    setImageURI(iconUrl, null)
}

fun AsyncImageView.bindBrowserNetworkIcon(chainId: String?) =
    bindBrowserNetworkIcon(listOfNotNull(chainId))
