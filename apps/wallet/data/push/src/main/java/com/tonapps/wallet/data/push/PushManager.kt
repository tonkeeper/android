package com.tonapps.wallet.data.push

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.extensions.locale
import com.tonapps.network.getBitmap
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.push.entities.AppPushEntity
import com.tonapps.wallet.data.push.entities.WalletPushEntity
import com.tonapps.wallet.data.push.source.LocalDataSource
import com.tonapps.wallet.data.push.source.RemoteDataSource
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.tonconnect.TonConnectRepository
import com.tonapps.wallet.data.tonconnect.entities.DAppManifestEntity
import com.tonapps.wallet.data.tonconnect.entities.DConnectEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PushManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val tonConnectRepository: TonConnectRepository,
    private val api: API
) {

    private val notificationChannelDefault = context.getString(R.string.default_push_channel_id)
    private val notificationChannelApp = context.getString(R.string.app_push_channel_id)

    private val recentlyReceiveAppPushIds = ArrayDeque<String>(10)
    private val helper = NotificationHelper(context)
    private val remoteDataSource = RemoteDataSource(api)
    private val localDataSource = LocalDataSource(context)

    private val _dAppPushFlow = MutableStateFlow<List<AppPushEntity>?>(null)
    val dAppPushFlow = _dAppPushFlow.asStateFlow()

    init {
        combine(
            settingsRepository.firebaseTokenFlow,
            settingsRepository.walletPush
        ) { token, _ ->
            subscribe(token)
        }.flowOn(Dispatchers.IO).launchIn(scope)

        accountRepository.selectedWalletFlow.onEach {
            _dAppPushFlow.value = getRemoteDAppEvents(it)
        }.launchIn(scope)
    }

    suspend fun getLocalDAppEvents(
        wallet: WalletEntity
    ): List<AppPushEntity> = withContext(Dispatchers.IO) {
        localDataSource.get(wallet.id)
    }

    suspend fun getRemoteDAppEvents(wallet: WalletEntity): List<AppPushEntity> = withContext(Dispatchers.IO) {
        val token = accountRepository.requestTonProofToken(wallet) ?: return@withContext emptyList()
        val items = remoteDataSource.getEvents(token, wallet.accountId)
        localDataSource.save(wallet.id, items)
        return@withContext items
    }

    suspend fun handleAppPush(push: AppPushEntity): Boolean = withContext(Dispatchers.IO) {
        if (!alreadyReceiveAppPush(push.from)) {
            try {
                val wallet = accountRepository.getWalletByAccountId(push.account, false) ?: throw IllegalStateException("Wallet not found")
                val connect = tonConnectRepository.getConnect(push.dappUrl, wallet) ?: throw IllegalStateException("App not found")
                localDataSource.insert(wallet.id, push)
                val largeIcon = api.defaultHttpClient.getBitmap(connect.manifest.iconUrl)
                displayAppPush(connect, connect.manifest, push, wallet, largeIcon)

                if (accountRepository.selectedWalletFlow.firstOrNull() == wallet) {
                    val old = _dAppPushFlow.value ?: emptyList()
                    _dAppPushFlow.value = old + push
                }
                return@withContext true
            } catch (ignored: Throwable) {}
        }
        return@withContext false
    }

    private suspend fun displayAppPush(
        connect: DConnectEntity,
        manifest: DAppManifestEntity,
        push: AppPushEntity,
        wallet: WalletEntity,
        largeIcon: Bitmap?
    ) = withContext(Dispatchers.Main) {
        val notificationId = helper.findIdOrNew {
            it.extras?.getCharSequence(Notification.EXTRA_TEXT) == push.message
        }

        val pending = helper.createPendingIntent(context, push.intent)
        val channel = helper.getChannel(notificationChannelApp)
        val builder = helper.baseBuilder(context, channel.id)
        builder.setContentTitle(manifest.name)
        builder.setContentText(push.message)
        builder.setColor(wallet.label.color)
        builder.setLargeIcon(largeIcon)
        builder.setContentIntent(pending)
        helper.display(notificationId, builder.build())
    }

    fun displayNotification(notification: Notification) {
        val id = helper.newId()
        helper.display(id, notification)
    }

    suspend fun handleWalletPush(push: WalletPushEntity) = withContext(Dispatchers.IO) {
        try {
            val wallet = accountRepository.getWalletByAccountId(push.account, false) ?: throw IllegalStateException("Wallet not found")
            displayWalletPush(push, wallet)
            return@withContext true
        } catch (ignored: Throwable) {}
        return@withContext false
    }

    private suspend fun displayWalletPush(
        push: WalletPushEntity,
        wallet: WalletEntity,
    ) = withContext(Dispatchers.Main) {
        val notificationId = helper.findIdOrNew {
            it.extras?.getCharSequence(Notification.EXTRA_TEXT) == push.notificationBody
        }

        val pending = helper.createPendingIntent(context, push.intent)
        val channel = helper.getChannel(notificationChannelApp)
        val builder = helper.baseBuilder(context, channel.id)
        builder.setContentTitle(wallet.label.title)
        builder.setContentText(push.notificationBody)
        builder.setColor(wallet.label.color)
        builder.setContentIntent(pending)
        helper.display(notificationId, builder.build())
    }

    private fun alreadyReceiveAppPush(from: String): Boolean {
        if (recentlyReceiveAppPushIds.contains(from)) {
            return true
        }
        recentlyReceiveAppPushIds.add(from)
        if (recentlyReceiveAppPushIds.size > 10) {
            recentlyReceiveAppPushIds.removeFirst()
        }
        return false
    }

    private suspend fun subscribe(
        firebaseToken: String
    ) = withContext(Dispatchers.IO) {
        val tcAppsDeferred = async { tonConnectRepository.subscribePush(firebaseToken) }
        val walletDeferred = async { subscribeWalletPush(firebaseToken) }

        if (!tcAppsDeferred.await()) {
            Log.e("TONKeeperLog", "Failed to subscribe to TC apps push")
        }

        if (!walletDeferred.await()) {
            Log.e("TONKeeperLog", "Failed to subscribe to wallet push")
        }
    }

    private suspend fun subscribeWalletPush(
        firebaseToken: String
    ): Boolean {
        val wallets = accountRepository.getWallets().filter { !it.testnet }

        val enabledAccounts = mutableListOf<String>()
        val disabledAccounts = mutableListOf<String>()

        for (wallet in wallets) {
            val address = wallet.accountId.toUserFriendly(testnet = false, bounceable = true)
            if (settingsRepository.getPushWallet(wallet.id)) {
                enabledAccounts.add(address)
            } else {
                disabledAccounts.add(address)
            }
        }

        val subscribed = api.pushSubscribe(
            locale = context.locale,
            firebaseToken = firebaseToken,
            deviceId = settingsRepository.installId,
            accounts = enabledAccounts.distinct()
        )

        val unsubscribed = api.pushUnsubscribe(
            deviceId = settingsRepository.installId,
            accounts = disabledAccounts.distinct()
        )

        return subscribed && unsubscribed
    }
}