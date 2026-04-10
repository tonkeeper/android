package com.tonapps.wallet.data.settings.entities

enum class PreferredTronFeeMethod(val id: Int) {
	UNSPECIFIED(0),
	TRX(1),
	TON(2),
	BATTERY(3);

	companion object {
		fun fromId(id: Int): PreferredTronFeeMethod {
			return entries.find { it.id == id }
				?: throw IllegalArgumentException("Invalid PreferredTronFeeMethod id: $id")
		}
	}
}

