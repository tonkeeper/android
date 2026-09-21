package com.tonapps.wc.models

import com.tonapps.wc.chains.WcRequestData

data class WcAppData(
    val name: String,
    val url: String,
    val description: String? = null,
    val icon: String? = null,
) : WcRequestData
