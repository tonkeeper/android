package com.tonapps.wallet.data.browser.entities

import android.graphics.Color
import android.net.Uri
import android.os.Parcelable
import com.tonapps.extensions.toUriOrNull
import io.tonendpointapi.models.PopularApp
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class BrowserAppEntity(
    val name: String,
    val description: String,
    val icon: Uri,
    val poster: Uri?,
    val url: Uri,
    val textColor: Int,
    val chains: List<String> = emptyList(),
    val button: Button? = null,
    val id: String = "",
    val bannerId: String? = null,
): Parcelable {

    @IgnoredOnParcel
    val useCustomTabs: Boolean
        get() = url.host?.endsWith("mercuryo.io") == true

    @IgnoredOnParcel
    val useTG: Boolean
        get() = url.host == "t.me"

    @IgnoredOnParcel
    val host: String?
        get() = url.host

    @Parcelize
    data class Button(
        val type: String,
        val payload: String,
        val title: String
    ): Parcelable

    constructor(app: PopularApp) : this(
        name = app.name,
        description = app.description,
        icon = Uri.parse(app.icon.orEmpty()),
        poster = Uri.parse(app.poster.orEmpty()),
        url = parseUrl(app.url),
        textColor = Color.parseColor(app.textColor ?: "#ffffff"),
        chains = app.chains.filter { it.isNotEmpty() },
        button = app.button?.let { Button(it.type, it.payload, it.title) },
        id = app.id,
        bannerId = app.bannerId?.takeIf { it.isNotEmpty() }
    )

    companion object {

        private fun parseUrl(value: String?): Uri {
            if (value.isNullOrBlank()) {
                return Uri.EMPTY
            }
            if (value.startsWith("http://")) {
                return parseUrl(value.replace("http://", "https://"))
            }
            val uri = value.toUriOrNull() ?: return Uri.EMPTY
            if (uri.scheme.isNullOrBlank()) {
                return parseUrl("https://$value")
            }
            return uri
        }
    }
}

fun List<BrowserAppEntity>.filterByChain(chainId: String?): List<BrowserAppEntity> {
    if (chainId == null) {
        return this
    }
    return filter { app ->
        app.chains.any { it.equals(chainId, ignoreCase = true) }
    }
}
