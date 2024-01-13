package com.tonkeeper.core.tonconnect.models

data class TCDomain(val domain: String) {

    val size: Int = domain.toByteArray().size
}