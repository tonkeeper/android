package com.tonapps.tonkeeper.manager.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.android.ext.android.inject
import kotlin.coroutines.resume

class FirebasePush: FirebaseMessagingService() {

    private val settingsRepository: SettingsRepository by inject()
    private val pushManager: PushManager by inject()

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        settingsRepository.firebaseToken = token
        pushManager.newFirebaseToken()
    }

    companion object {

        suspend fun requestToken(): String? = suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    continuation.resume(null)
                } else {
                    Log.d("FirebasePusLog", "token: ${task.result}")
                    continuation.resume(task.result)
                }
            }
        }
    }
}