package core.extensions

import android.util.Base64

/*fun ByteArray.toBase64(): String {
    return Base64.encodeToString(this, Base64.NO_WRAP)
}

fun String.toByteArrayFromBase64(): ByteArray {
    return Base64.decode(this, Base64.NO_WRAP)
}

fun ByteArray.toHex(): String {
    return joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
}

fun String.hexToByteArray(): ByteArray {
    val length = this.length
    val data = ByteArray(length / 2)
    for (i in 0 until length step 2) {
        data[i / 2] = ((this[i].digitToInt(16) shl 4) + this[i + 1].digitToInt(16)).toByte()
    }
    return data
}*/