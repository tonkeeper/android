package ton.extensions

import org.ton.block.AddrStd

fun String.toUserFriendly(
    wallet: Boolean = true
): String {
    if (startsWith("UQ")) {
        return this
    }
    return try {
        val addr = AddrStd(this)
        if (wallet) {
            addr.toUserFriendly()
        } else {
            addr.toString(userFriendly = true)
        }
    } catch (e: Exception) {
        this
    }
}

fun AddrStd.toUserFriendly(): String {
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