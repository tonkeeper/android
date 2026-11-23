package com.tonapps.wallet.api.entity

import android.os.Parcelable
import io.tonendpointapi.models.Banner
import kotlinx.parcelize.Parcelize

@Parcelize
data class BannerEntity(
    val id: String,
    val title: String,
    val description: String,
    val image: String?,
    val textColor: String?,
    val backgroundColor: String?,
    val button: Button?
): Parcelable {

    @Parcelize
    data class Button(
        val type: Type,
        val payload: String,
        val title: String
    ): Parcelable {

        enum class Type {
            LINK,
            DEEPLINK;

            companion object {

                fun of(value: String): Type {
                    return when (value.lowercase()) {
                        "deeplink" -> DEEPLINK
                        else -> LINK
                    }
                }
            }
        }
    }

    constructor(banner: Banner) : this(
        id = banner.id,
        title = banner.title,
        description = banner.description,
        image = banner.image?.takeIf { it.isNotBlank() },
        textColor = banner.textColor?.takeIf { it.isNotBlank() },
        backgroundColor = banner.backgroundColor?.takeIf { it.isNotBlank() },
        button = banner.button?.let { Button(Button.Type.of(it.type), it.payload, it.title) }
    )
}
