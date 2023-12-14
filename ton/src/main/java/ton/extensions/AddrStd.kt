package ton.extensions

import org.ton.block.AddrStd

fun String.toUserFriendly(): String {
    if (startsWith("UQ")) {
        return this
    }
    return try {
        AddrStd(this).toUserFriendly()
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