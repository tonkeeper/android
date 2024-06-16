package com.tonapps.wallet.data.rn.expo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

class AuthenticationHelper {

    var activity: FragmentActivity? = null

    private var isAuthenticating = false

    suspend fun authenticateCipher(cipher: Cipher, requiresAuthentication: Boolean, title: String): Cipher {
        if (requiresAuthentication) {
            return openAuthenticationPrompt(cipher, title).cryptoObject?.cipher
                ?: throw AuthenticationException("Couldn't get cipher from authentication result")
        }
        return cipher
    }

    private suspend fun openAuthenticationPrompt(
        cipher: Cipher,
        title: String
    ): BiometricPrompt.AuthenticationResult {
        if (isAuthenticating) {
            throw AuthenticationException("Authentication is already in progress")
        }

        isAuthenticating = true

        assertBiometricsSupport()
        val fragmentActivity = activity ?: throw AuthenticationException("Cannot display biometric prompt when the app is not in the foreground")

        val authenticationPrompt = AuthenticationPrompt(fragmentActivity, title)

        return withContext(Dispatchers.Main.immediate) {
            try {
                return@withContext authenticationPrompt.authenticate(cipher)
                    ?: throw AuthenticationException("Couldn't get the authentication result")
            } finally {
                isAuthenticating = false
            }
        }
    }

    fun assertBiometricsSupport() {
        val fragmentActivity = activity ?: throw AuthenticationException("Cannot display biometric prompt when the app is not in the foreground")

        val biometricManager = BiometricManager.from(fragmentActivity)
        @SuppressLint("SwitchIntDef") // BiometricManager.BIOMETRIC_SUCCESS shouldn't do anything
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE, BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                throw AuthenticationException("No hardware available for biometric authentication. Use expo-local-authentication to check if the device supports it")
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                throw AuthenticationException("No biometrics are currently enrolled")
            }
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                throw AuthenticationException("An update is required before the biometrics can be used")
            }
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                throw AuthenticationException("Biometric authentication is unsupported")
            }
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                throw AuthenticationException("Biometric authentication status is unknown")
            }
        }
    }

    companion object {
        const val REQUIRE_AUTHENTICATION_PROPERTY = "requireAuthentication"
    }
}