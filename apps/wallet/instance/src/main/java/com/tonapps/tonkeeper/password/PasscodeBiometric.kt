package com.tonapps.tonkeeper.password

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.tonapps.wallet.localization.Localization
import uikit.extensions.activity

object PasscodeBiometric {

    private const val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL or BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun isAvailableOnDevice(context: Context): Boolean {
        val authStatus = BiometricManager.from(context).canAuthenticate(authenticators)
        return authStatus == BiometricManager.BIOMETRIC_SUCCESS || authStatus == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
    }

    fun showPrompt(context: Context, callback: BiometricPrompt.AuthenticationCallback) {
        val activity = context.activity as? FragmentActivity
        if (activity == null) {
            callback.onAuthenticationError(BiometricPrompt.ERROR_HW_NOT_PRESENT, "Activity not found")
            return
        }
        try {
            val mainExecutor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(activity, mainExecutor, callback)
            val builder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(Localization.app_name))
                .setAllowedAuthenticators(authenticators)
                .setConfirmationRequired(false)

            biometricPrompt.authenticate(builder.build())
        } catch (e: Throwable) {
            callback.onAuthenticationError(BiometricPrompt.ERROR_HW_NOT_PRESENT, "Unknown error")
        }
    }
}