package com.tonkeeper.core.tonconnect.models

import com.tonkeeper.BuildConfig
import kotlinx.serialization.Serializable

@Serializable
data class TCDevice(
    val platform: String = "android",
    val appName: String = "Tonkeeper Y",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val maxProtocolVersion: Int = 2,
    // val features: List<Any> = emptyList()
)