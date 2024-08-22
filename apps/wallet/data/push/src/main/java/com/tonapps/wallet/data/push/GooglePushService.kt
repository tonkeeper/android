package com.tonapps.wallet.data.push

import android.os.Bundle
import androidx.annotation.WorkerThread
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tonapps.wallet.data.push.entities.AppPushEntity
import com.tonapps.wallet.data.push.entities.WalletPushEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.android.ext.android.inject
import kotlin.coroutines.resume

class GooglePushService: FirebaseMessagingService() {

    private lateinit var scopeMain: CoroutineScope

    private val pushManager: PushManager by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val recentlyReceivedMessageIds = ArrayDeque<String>(10)

    override fun onCreate() {
        super.onCreate()
        scopeMain = CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    private suspend fun onPushProcess(message: RemoteMessage): Boolean {
        return try {
            message.toIntent().extras?.let { onPushProcess(it) } ?: false
        } catch (ignored: Throwable) {
            false
        }
    }

    private suspend fun onPushProcess(extras: Bundle): Boolean {
        val firebaseMessageId = extras.getString("google.message_id") ?: return false
        if (alreadyReceivedMessage(firebaseMessageId)) {
            return true
        }

        val pushType = extras.getString("type") ?: return false
        return when (pushType) {
            TYPE_DAPP -> pushManager.handleAppPush(AppPushEntity(extras))
            else -> pushManager.handleWalletPush(WalletPushEntity(extras))
        }
    }

    private fun alreadyReceivedMessage(messageId: String): Boolean {
        if (recentlyReceivedMessageIds.contains(messageId)) {
            return true
        }
        recentlyReceivedMessageIds.add(messageId)
        if (recentlyReceivedMessageIds.size > 10) {
            recentlyReceivedMessageIds.removeFirst()
        }
        return false
    }

    @WorkerThread
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        /*scopeMain.launch(Dispatchers.IO) {
            val processed = onPushProcess(message)
            if (!processed) {
                message.notification?.let {
                    // pushManager.displayNotification(it.)
                }
            }
        }*/
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        settingsRepository.firebaseToken = token
    }

    override fun onDestroy() {
        super.onDestroy()
        scopeMain.cancel()
    }

    companion object {

        private const val TYPE_DAPP = "console_dapp_notification"
        private const val TYPE_BRIDGE_DAPP_NOTIFICATION = "bridge_dapp_notification"

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