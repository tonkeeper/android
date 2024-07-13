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
        val salt = state.salt.hex()
        val passcodeHash = passcodeHash(passcode, salt)
        val nonce = salt.copyOfRange(0, 24)
        val clearText = state.ciphertext.hex()
        val pt = Sodium.cryptoSecretboxOpen(clearText, nonce, passcodeHash) ?: throw Exception("cryptoSecretboxOpen failed")
        return pt.decodeToString()
    }

}
