package com.tonapps.blockchain.ton.extensions

import org.ton.block.AddrStd

fun AddrStd.toWalletAddress(testnet: Boolean): String {
    return toString(
        userFriendly = true,
        bounceable = false,
        testOnly = testnet,
    )
}

fun AddrStd.toAccountId(): String {
    return toString(
        userFriendly = false,
    ).lowercase()
}

fun String.toUserFriendly(
    wallet: Boolean = true,
    testnet: Boolean
): String {
    return try {
        val addr = AddrStd(this)
        if (wallet) {
            addr.toWalletAddress(testnet)
        } else {
            addr.toString(userFriendly = true)
        }
    } catch (e: Exception) {
        this
    }
}

fun String.toRawAddress(): String {
    return try {
        AddrStd(this).toString(userFriendly = false).lowercase()
    } catch (e: Exception) {
        this
    }
}

fun String.isValidTonAddress(): Boolean {
    return try {
        AddrStd(this)
        true
    } catch (e: Exception) {
        false
    }
}