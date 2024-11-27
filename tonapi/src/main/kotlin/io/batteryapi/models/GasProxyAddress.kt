package io.batteryapi.models

import com.squareup.moshi.Json

data class GasProxyAddress(
    @Json(name = "address")
    val address: String
)