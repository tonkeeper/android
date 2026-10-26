package com.tonapps.tonkeeper.manager.push

import android.annotation.SuppressLint
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tonapps.core.deeplink.DeepLinkRoute
import com.tonapps.log.L
import com.tonapps.perps.PerpsScreenTracker
import com.tonapps.tonkeeper.helper.NotificationsHelper
import com.tonapps.tonkeeper.ui.screen.root.RootActivity
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppPushEntity
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.android.ext.android.inject
import kotlin.coroutines.resume

class FirebasePush: FirebaseMessagingService() {

    private val settingsRepository: SettingsRepository by inject()
    private val pushManager: PushManager by inject()
    private val dAppsRepository: DAppsRepository by inject()
    private val unifiedAccountRepository: UnifiedAccountRepository by inject()
    private val perpsScreenTracker: PerpsScreenTracker by inject()
    private val passcodeManager: PasscodeManager by inject()

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (message.data["type"] == "console_dapp_notification") {
            onDAppPushReceived(message.data)
        } else {
            onPerpsPushReceived(message)
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

    @SuppressLint("MissingPermission")
    private fun onPerpsPushReceived(message: RemoteMessage) {
        try {
            val data = message.data
            val route = DeepLinkRoute.pushLink(data::get)?.let(DeepLinkRoute::resolve)
            if (route !is DeepLinkRoute.Perps && data["type"]?.startsWith("perps_") != true) {
                return
            }
            val onScreen = when (route) {
                is DeepLinkRoute.Perps.Home -> perpsScreenTracker.isShowing(null)
                is DeepLinkRoute.Perps.Market -> perpsScreenTracker.isShowing(route.marketIndex)
                else -> false
            }
            val pushWalletId = data["wallet_id"]
            val sameWallet = pushWalletId == null || pushWalletId == unifiedAccountRepository.getSelectedWalletId()
            if (sameWallet && onScreen && passcodeManager.lockscreenHiddenFlow.value) {
                return
            }
            val title = message.notification?.title ?: data["title"]
            val body = message.notification?.body ?: data["body"]
            if (title.isNullOrBlank() && body.isNullOrBlank()) {
                return
            }
            val manager = NotificationManagerCompat.from(this)
            if (!manager.areNotificationsEnabled()) {
                return
            }
            val channelId = getString(R.string.default_push_channel_id)
            NotificationsHelper.getOrCreatePushChannel(this, channelId)
            val intent = Intent(this, RootActivity::class.java)
            data.forEach { (key, value) -> intent.putExtra(key, value) }
            val requestCode = (message.messageId ?: data["push_id"] ?: System.currentTimeMillis().toString()).hashCode()
            val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_push)
                .setColor(ContextCompat.getColor(this, com.tonapps.ui.uikit.color.R.color.constantBlue))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(NotificationsHelper.getPendingIntent(this, intent, requestCode))
                .build()
            manager.notify(requestCode, notification)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (BuildConfig.DEBUG) {
            L.d("[PUSH_TOKEN] New push token generated: $token")
        }

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
