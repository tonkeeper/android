package com.tonapps.tonkeeper.manager.push

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppPushEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.android.ext.android.inject
import kotlin.coroutines.resume

class FirebasePush: FirebaseMessagingService() {

    private val settingsRepository: SettingsRepository by inject()
    private val pushManager: PushManager by inject()
    private val dAppsRepository: DAppsRepository by inject()

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val pushType = data["type"] ?: return
        if (pushType == "console_dapp_notification") {
            onDAppPushReceived(data)
        }
    }

    private fun onDAppPushReceived(data: Map<String, String>) {
        try {
            val body = AppPushEntity.Body(data)
            dAppsRepository.insertDAppNotification(body)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
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
                    continuation.resume(task.result)
                }
            }
        }
    }
}