package ton.extensions

import org.ton.api.pub.PublicKeyEd25519
import org.ton.crypto.base64

fun PublicKeyEd25519.base64(): String {
    return base64(key.toByteArray())
}