package com.tonapps.wallet.data.push

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.WorkerThread
import com.google.firebase.messaging.Constants
import com.google.firebase.messaging.Constants.MessagePayloadKeys
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tonapps.wallet.data.push.PushManager
import com.tonapps.wallet.data.push.entities.AppPushEntity
import com.tonapps.wallet.data.push.entities.WalletPushEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.android.ext.android.inject
import kotlin.coroutines.resume

class GooglePushService: FirebaseMessagingService() {

    private val pushManager: PushManager by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val recentlyReceivedMessageIds = ArrayDeque<String>(10)

    private fun onPushReceived(extras: Bundle) {
        val firebaseMessageId = extras.getString("google.message_id") ?: return
        if (alreadyReceivedMessage(firebaseMessageId)) {
            return
        }

        val pushType = extras.getString("type") ?: return
        when (pushType) {
            TYPE_DAPP -> pushManager.handleAppPush(AppPushEntity(extras))
            TYPE_BRIDGE_DAPP_NOTIFICATION -> {

            }
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

    override fun handleIntent(intent: Intent) {
        super.handleIntent(intent)
        // bad hack to handle push messages from firebase
        if (intent.action == "com.google.android.c2dm.intent.RECEIVE") {
            val messageType = intent.getStringExtra(MessagePayloadKeys.MESSAGE_TYPE) ?: Constants.MessageTypes.MESSAGE
            if (messageType == Constants.MessageTypes.MESSAGE) {
                intent.extras?.let {
                    onPushReceived(it)
                }
            }
        }
    }

    @WorkerThread
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        try {
            message.toIntent().extras?.let {
                onPushReceived(it)
            }
        } catch (ignored: Throwable) {}
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        settingsRepository.firebaseToken = token
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