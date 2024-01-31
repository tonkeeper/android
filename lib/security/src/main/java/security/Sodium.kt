package security

object Sodium {

    init {
        System.loadLibrary("libsodium")
        init()
    }

    external fun init(): Int

    external fun argon2IdHash(
        password: CharArray,
        salt: ByteArray,
        hashSize: Int
    ): ByteArray?
}