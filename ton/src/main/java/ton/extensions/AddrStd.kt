package ton.extensions

import org.ton.block.AddrStd

fun String.toUserFriendly(
    wallet: Boolean = true
): String {
    return try {
        val addr = AddrStd(this)
        if (wallet) {
            addr.toWalletAddress()
        } else {
            addr.toString(userFriendly = true)
        }
    } catch (e: Exception) {
        this
    }
}

fun AddrStd.toWalletAddress(): String {
    return toString(
        userFriendly = true,
        bounceable = false
    )
}

fun String.toRawAddress(): String {
    return try {
        AddrStd(this).toString(userFriendly = false).lowercase()
    } catch (e: Exception) {
        this
    }
}