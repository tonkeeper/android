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

fun String.isTestnetAddress(): Boolean {
    return startsWith("0Q") || startsWith("kQ")
}

fun String.isBounceable(): Boolean {
    return startsWith("0:") || startsWith("E")
}

fun String.toUserFriendly(
    wallet: Boolean = true,
    testnet: Boolean,
    bounceable: Boolean = true,
): String {
    return try {
        val addr = AddrStd(this)
        if (wallet) {
            addr.toWalletAddress(testnet)
        } else {
            addr.toString(userFriendly = true, bounceable = bounceable)
        }
    } catch (e: Exception) {
        this
    }
}

fun String.toRawAddress(): String {
    if (this.contains(":")) {
        return this
    }
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

fun String.isValidTonDomain(): Boolean {
    return endsWith(".ton")
}

fun String.equalsAddress(other: String): Boolean {
    return try {
        toRawAddress().equals(other.toRawAddress(), ignoreCase = true)
    } catch (e: Throwable) {
        false
    }
}