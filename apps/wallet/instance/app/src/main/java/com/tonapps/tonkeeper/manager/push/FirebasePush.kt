package com.tonapps.tonkeeper.manager.push

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FirebasePush: FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d("FirebasePushLog", "onMessageReceived: ${message.data}")
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FirebasePushLog", "onNewToken: $token")
    }

    override fun handleIntent(intent: Intent?) {
        super.handleIntent(intent)
        Log.d("FirebasePushLog", "handleIntent: ${intent?.extras}")
    }

    companion object {

        suspend fun requestToken(): String? = suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    continuation.resume(null)
                } else {
                    continuation.resume(task.result)
                }
            }
        }
    }
}