package com.tonapps.wallet.api.configs

data class CountryConfig(
    val deviceCountry: String? = null,
    val storeCountry: String? = null,
    val simCountry: String? = null,
    val timezone: String? = null,
    val isVpn: Boolean? = null,
)
