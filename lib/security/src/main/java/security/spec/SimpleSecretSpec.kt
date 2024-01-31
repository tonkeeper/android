package security.spec

import android.util.Log
import security.clear
import java.security.spec.KeySpec
import javax.crypto.SecretKey

class SimpleSecretSpec(
    input: ByteArray,
    private val algorithm: String = "AES"
): KeySpec, SecretKey {

    private val key: ByteArray

    init {
        key = input.copyOf()
        input.clear()
    }

    override fun getAlgorithm() = algorithm

    override fun getFormat() = "RAW"

    override fun getEncoded() = key.copyOf()

    override fun destroy() {
        key.fill(0)
    }

    override fun isDestroyed(): Boolean {
        return key.isEmpty()
    }
}