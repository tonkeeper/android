package com.tonapps.security

object Sodium {

    init {
        System.loadLibrary("libsodium")
        init()
    }

    external fun init(): Int

    external fun cryptoBoxNonceBytes(): Int

    external fun cryptoBoxMacBytes(): Int

    external fun cryptoBoxEasy(
        cipher: ByteArray,
        plain: ByteArray,
        plainSize: Int,
        nonce: ByteArray,
        remotePublicKey: ByteArray,
        localPrivateKey: ByteArray
    ): Int

    external fun cryptoBoxOpenEasy(
        plain: ByteArray,
        cipher: ByteArray,
        cipherSize: Int,
        nonce: ByteArray,
        remotePublicKey: ByteArray,
        localPrivateKey: ByteArray
    ): Int

    external fun cryptoBoxKeyPair(
        publicKey: ByteArray,
        privateKey: ByteArray
    ): Int

    external fun argon2IdHash(
        password: CharArray,
        salt: ByteArray,
        hashSize: Int
    ): ByteArray?
}