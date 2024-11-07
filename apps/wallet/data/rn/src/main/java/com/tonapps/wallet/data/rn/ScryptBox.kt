package com.tonapps.wallet.data.rn

import com.tonapps.security.Security
import com.tonapps.security.Sodium
import com.tonapps.security.hex

internal object ScryptBox {

    private fun passcodeHash(
        passcode: String,
        salt: ByteArray,
    ): ByteArray {
        return Sodium.scryptHash(
            password = passcode.toByteArray(),
            salt = salt,
            n = SeedState.n,
            r = SeedState.r,
            p = SeedState.p,
            dkLen = 32
        ) ?: throw Exception("scryptHash failed")
    }

    fun encrypt(passcode: String, value: String): SeedState {
        val salt = Security.randomBytes(32)
        val passcodeHash = passcodeHash(passcode, salt)
        val nonce = salt.copyOfRange(0, 24)
        val ciphertext = Sodium.cryptoSecretbox(
            text = value.toByteArray(),
            nonce = nonce,
            key = passcodeHash
        ) ?: throw Exception("cryptoSecretbox failed")

        return SeedState(
            salt = hex(salt),
            ciphertext = hex(ciphertext)
        )
    }

    fun decrypt(passcode: String, state: SeedState): String {
        if (state.kind != "encrypted-scrypt-tweetnacl") {
            throw Exception("Invalid state kind")
        }
        val bytes = decrypt1(passcode, state) ?: decrypt2(passcode, state) ?: throw Exception("cryptoSecretboxOpen failed")
        return bytes.decodeToString()
    }

    private fun decrypt1(passcode: String, state: SeedState): ByteArray? {
        val salt = state.salt.hex()
        val passcodeHash = passcodeHash(passcode, salt)
        val nonce = salt.copyOfRange(0, 24)
        val clearText = state.ciphertext.hex()
        return cryptoSecretBoxOpen(clearText, nonce, passcodeHash)
    }

    private fun decrypt2(passcode: String, state: SeedState): ByteArray? {
        val salt = org.ton.crypto.hex(state.salt)
        val passcodeHash = passcodeHash(passcode, salt)
        val nonce = salt.copyOfRange(0, 24)
        val clearText = org.ton.crypto.hex(state.ciphertext)
        return cryptoSecretBoxOpen(clearText, nonce, passcodeHash)
    }

    private fun cryptoSecretBoxOpen(
        box: ByteArray,
        nonce: ByteArray,
        key: ByteArray
    ): ByteArray? {
        return Sodium.cryptoSecretboxOpen(box, nonce, key)
    }

}
