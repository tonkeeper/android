package com.tonapps.wallet.data.collectibles.entities

import android.os.Parcelable
import android.util.Base64
import android.util.Log
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class NftMetadataEntity(
    val strings: HashMap<String, String>,
    val buttons: List<Button>
): Parcelable {

    @Parcelize
    data class Button(
        val label: String,
        val uri: String
    ): Parcelable {

        constructor(map: Map<String, String>) : this(
            label = map["label"] ?: "",
            uri = map["uri"] ?: ""
        )
    }

    @IgnoredOnParcel
    val name: String?
        get() = strings["name"]

    @IgnoredOnParcel
    val description: String?
        get() = strings["description"]

    @IgnoredOnParcel
    val isNotRender: Boolean
        get() = strings["render_type"] == "hidden"

    @IgnoredOnParcel
    val lottie: String? by lazy {
        val originalUrl = strings["lottie"] ?: return@lazy null
        val encoded = Base64.encodeToString(originalUrl.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING).trim()
        "https://c.tonapi.io/json?url=$encoded"
    }

    constructor(map: Map<String, Any>) : this(
        strings = map.filter { it.value is String }.mapValues { it.value as String } as HashMap<String, String>,
        buttons = map["buttons"]?.let { buttons ->
            (buttons as List<Map<String, String>>).map {
                Button(it)
            }
        } ?: arrayListOf()
    )
}