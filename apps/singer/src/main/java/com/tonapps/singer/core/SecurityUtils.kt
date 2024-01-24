package com.tonapps.singer.core

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2KtResult
import com.lambdapioneer.argon2kt.Argon2Mode
import java.security.SecureRandom

object SecurityUtils {

    private val rand = SecureRandom.getInstanceStrong()
    private val argon2Kt = Argon2Kt()

    fun randomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        rand.nextBytes(bytes)
        return bytes
    }

    fun argon2Hash(password: String, salt: ByteArray): String {
        return argon2Hash(password.toByteArray(Charsets.US_ASCII), salt)
    }

    private fun argon2Hash(password: ByteArray, salt: ByteArray): String {
        val hashResult: Argon2KtResult = argon2Kt.hash(
            mode = Argon2Mode.ARGON2_I,
            password = password,
            salt = salt,
        )
        return hashResult.encodedOutputAsString()
    }

    fun argon2Verify(password: String, hash: String): Boolean {
        return argon2Verify(password.toByteArray(Charsets.US_ASCII), hash)
    }

    private fun argon2Verify(password: ByteArray, hash: String): Boolean {
        return argon2Kt.verify(
            mode = Argon2Mode.ARGON2_I,
            encoded = hash,
            password = password,
        )
    }
}