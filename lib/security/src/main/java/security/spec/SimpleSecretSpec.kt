package security.spec

import security.clear
import java.io.Closeable
import java.security.spec.KeySpec
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.SecretKey

class SimpleSecretSpec(
    input: ByteArray,
    private val algorithm: String = "AES"
): KeySpec, SecretKey, Closeable {

    private var key: ByteArray? = null

    init {
        key = input.copyOf()
        input.clear()
    }

    override fun getAlgorithm() = algorithm

    override fun getFormat() = "RAW"

    override fun getEncoded(): ByteArray {
        if (key == null) {
            throw IllegalStateException("Key has been destroyed")
        }
        return key!!.copyOf()
    }

    override fun destroy() {
        close()
    }

    override fun close() {
        clear()
    }

    fun clear() {
        key?.fill(0)
        key = null
    }

    override fun isDestroyed(): Boolean {
        return key == null
    }
}