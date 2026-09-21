package com.tonapps.wallet.data.passcode

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.extensions.activity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object PasscodeBiometric {

    private const val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun isAvailableOnDevice(context: Context): Boolean {
        val authStatus = BiometricManager.from(context).canAuthenticate(authenticators)
        return authStatus == BiometricManager.BIOMETRIC_SUCCESS // || authStatus == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
    }

    suspend fun showPrompt(
        context: Context,
        title: String,
        passcodeFallback: Boolean = false
    ): Boolean = suspendCancellableCoroutine { continuation ->
        showPrompt(context, title, passcodeFallback, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                continuation.resume(false)
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // Yes. Biometric right now is only UI feature.
                continuation.resume(true)
            }
        })
    }

    fun showPrompt(
        context: Context,
        title: String,
        passcodeFallback: Boolean = false,
        callback: BiometricPrompt.AuthenticationCallback
    ) {
        val activity = context.activity as? FragmentActivity
        if (activity == null) {
            callback.onAuthenticationError(BiometricPrompt.ERROR_HW_NOT_PRESENT, "Activity not found")
            return
        }
        try {
            val mainExecutor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(activity, mainExecutor, callback)
            val builder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setAllowedAuthenticators(authenticators)
                .setConfirmationRequired(false)
                .setNegativeButtonText(negativeButtonText(context, passcodeFallback))

            biometricPrompt.authenticate(builder.build())
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            callback.onAuthenticationError(BiometricPrompt.ERROR_HW_NOT_PRESENT, "Unknown error")
        }
    }

    private fun negativeButtonText(context: Context, passcodeFallback: Boolean): String {
        return if (passcodeFallback) {
            context.getString(Localization.passcode_enter)
        } else {
            context.getString(android.R.string.cancel)
        }
    }
}