package com.tonapps.wallet.data.dapps.entities

import android.net.Uri
import android.os.Parcelable
import androidx.core.net.toUri
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class AppEntity(
    val url: Uri,
    val name: String,
    val iconUrl: String,
    val empty: Boolean
): Parcelable {

    @IgnoredOnParcel
    val id: String
        get() = url.toString()

    @IgnoredOnParcel
    val host: String
        get() = url.host ?: id

    @IgnoredOnParcel
    val isBadIcon: Boolean by lazy {
        iconUrl.isBlank() || iconUrl.endsWith("favicon.ico")
    }

    @IgnoredOnParcel
    val iconByFavicon: String by lazy {
        "https://www.google.com/s2/favicons?sz=256&domain=$host"
    }

    companion object {

        private const val MAX_URL_LENGTH = 2048
        private const val MAX_NAME_LENGTH = 256
        private const val MAX_ICON_URL_LENGTH = 32 * 1024

        val ETHENA_BASE_URL = "https://ethena.ston.fi".toUri()

        val ethena = AppEntity(
            url = ETHENA_BASE_URL,
            name = "ethena.ston.fi",
            iconUrl = "https://static.ston.fi/logo/external-logo.jpg",
            empty = false
        )

        fun fromManifest(value: String): AppEntity {
            val json = JSONObject(value)
            return sizeLimited(
                url = json.getString("url").removeSuffix("/"),
                name = json.getString("name").ifBlank {
                    throw IllegalArgumentException("name is empty")
                },
                iconUrl = json.getString("iconUrl").ifBlank {
                    throw IllegalArgumentException("iconUrl is empty")
                },
            )
        }

        fun sizeLimited(url: String, name: String, iconUrl: String): AppEntity {
            val app = AppEntity(
                url = url.take(MAX_URL_LENGTH).toUri(),
                name = name.take(MAX_NAME_LENGTH),
                iconUrl = iconUrl,
                empty = false,
            )
            return if (iconUrl.length > MAX_ICON_URL_LENGTH) {
                app.copy(iconUrl = app.iconByFavicon)
            } else {
                app
            }
        }
    }
}
