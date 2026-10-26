package com.tonapps.wallet.data.passcode

import android.app.Activity
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tonapps.extensions.activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

private const val REQUEST_KEY_PREFIX = "device_credential_"

internal object DeviceCredential {

    private val requestCounter = AtomicInteger()

    suspend fun confirm(context: Context, title: String): Boolean = withContext(Dispatchers.Main) {
        val activity = context.activity ?: return@withContext false
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            return@withContext false
        }
        val keyguardManager = activity.getSystemService(KeyguardManager::class.java) ?: return@withContext false

        @Suppress("DEPRECATION")
        val intent = keyguardManager.createConfirmDeviceCredentialIntent(title, null) ?: return@withContext false

        var launcher: ActivityResultLauncher<Intent>? = null
        var destroyObserver: LifecycleEventObserver? = null
        try {
            suspendCancellableCoroutine { continuation ->
                fun complete(confirmed: Boolean) {
                    if (continuation.isActive) {
                        continuation.resume(confirmed)
                    }
                }

                destroyObserver = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_DESTROY) {
                        complete(false)
                    }
                }.also { activity.lifecycle.addObserver(it) }

                launcher = activity.activityResultRegistry.register(
                    REQUEST_KEY_PREFIX + requestCounter.incrementAndGet(),
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    complete(result.resultCode == Activity.RESULT_OK)
                }
                try {
                    launcher?.launch(intent)
                } catch (_: ActivityNotFoundException) {
                    complete(false)
                }
            }
        } finally {
            launcher?.unregister()
            destroyObserver?.let { activity.lifecycle.removeObserver(it) }
        }
    }
}
