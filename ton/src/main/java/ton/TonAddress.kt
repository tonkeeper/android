package com.tonkeeper.ton

import org.ton.block.AddrStd

data class TonAddress(
    private val address: AddrStd
) {

    constructor(address: String) : this(AddrStd(address))

    override fun toString() = AddrStd.toString(address)

    override fun equals(other: Any?): Boolean {
        if (other is TonAddress) {
            return address == other.address
        }
        return false
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }

    fun raw() = AddrStd.toString(
        address,
        userFriendly = false
    )
}