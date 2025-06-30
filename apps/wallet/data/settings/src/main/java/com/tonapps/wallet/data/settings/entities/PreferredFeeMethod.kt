package com.tonapps.wallet.data.settings.entities

enum class PreferredFeeMethod(val id: Int) {
    UNSPECIFIED(0),
    TON(1),
    GASLESS(2),
    BATTERY(3);

    companion object {
        fun fromId(id: Int): PreferredFeeMethod {
            return values().find { it.id == id }
                ?: throw IllegalArgumentException("Invalid PreferredFeeMethod id: $id")
        }
    }
}