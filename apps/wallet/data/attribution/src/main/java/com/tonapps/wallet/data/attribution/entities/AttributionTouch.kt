package com.tonapps.wallet.data.attribution.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AttributionTouch(
    val provider: AttributionProvider,
    val event: AttributionEvent,
    val attributedAt: Long,
    val link: String? = null,
    val params: Map<String, String> = emptyMap(),
)

@Serializable
enum class AttributionProvider {
    @SerialName("branch")
    Branch,

    @SerialName("linkrunner")
    Linkrunner,
}

@Serializable
enum class AttributionEvent {
    @SerialName("install")
    Install,

    @SerialName("open")
    Open,
}
