package com.tonapps.tonkeeper.manager.push

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.tonapps.extensions.locale
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import io.walletapi.models.SubscribeDevicePushRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PushManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val api: API,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val dAppsRepository: DAppsRepository,
) {

    enum class State(val code: Int) {
        Enable(1), Disable(0), Delete(-1);

        companion object {
            fun of(code: Int): State {
                return entries.firstOrNull { it.code == code } ?: Disable
            }
        }
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    private val multichainPushMutex = Mutex()

    fun clearNotifications() {
        notificationManager.cancelAll()
    }

    fun newFirebaseToken() {
        scope.launch(Dispatchers.IO) {
            val enabled = unifiedAccountRepository.getTonWallets().filter { isPushEnabled(it) }
            val legacy = enabled.filter { it.type != WalletType.Multichain && !it.testnet }

            supervisorScope {
                val legacyTask = async {
                    runCatching { walletSubscribe(legacy) }.getOrDefault(false)
                }
                val multichainTask = async {
                    runCatching { syncMultichainPush() }.getOrDefault(false)
                }

                legacyTask.await()
                multichainTask.await()
            }
        }
    }

    suspend fun wallet(wallet: WalletEntity, state: State) = wallets(listOf(wallet), state)

    suspend fun wallets(wallets: List<WalletEntity>, state: State): Boolean = withContext(Dispatchers.IO) {
        val (multichain, legacy) = wallets.partition { it.type == WalletType.Multichain }

        supervisorScope {
            val legacyTask = async {
                runCatching {
                    if (state == State.Enable) {
                        walletSubscribe(legacy.filter { !it.testnet })
                    } else {
                        walletUnsubscribe(legacy, state == State.Delete)
                    }
                }.getOrDefault(false)
            }

            val multichainTask = async {
                runCatching { multichainPush(multichain, state) }.getOrDefault(false)
            }

            val legacyResult = legacyTask.await()
            val multichainResult = multichainTask.await()

            legacyResult && multichainResult
        }
    }

    private suspend fun multichainPush(wallets: List<WalletEntity>, state: State): Boolean {
        if (wallets.isEmpty()) {
            return true
        }

        return multichainPushMutex.withLock {
            val enable = state == State.Enable
            val changed = wallets.map { it.id }.toSet()
            val walletIds = enabledMultichainWalletIds().toMutableSet()

            if (enable) {
                walletIds.addAll(changed)
            } else {
                walletIds.removeAll(changed)
            }

            if (!sendMultichainPush(walletIds.toList())) {
                return@withLock false
            }

            for (wallet in wallets) {
                settingsRepository.setPushWallet(wallet.id, enable)
            }

            true
        }
    }

    private suspend fun syncMultichainPush(): Boolean = multichainPushMutex.withLock {
        sendMultichainPush(enabledMultichainWalletIds())
    }

    private suspend fun enabledMultichainWalletIds(): List<String> {
        return unifiedAccountRepository.getTonWallets()
            .filter { it.type == WalletType.Multichain && isPushEnabled(it) }
            .map { it.id }
    }

    private suspend fun sendMultichainPush(walletIds: List<String>): Boolean {
        return try {
            if (walletIds.isEmpty()) {
                api.multichain.auth.unsubscribeDevicePush()
            } else {
                val firebaseToken = getFirebaseToken()
                    ?: throw IllegalStateException("Firebase token not found")
                api.multichain.auth.subscribeDevicePush(
                    SubscribeDevicePushRequest(
                        pushToken = firebaseToken,
                        locale = context.locale.toLanguageTag(),
                        walletIds = walletIds,
                    )
                )
            }
            true
        } catch (e: Throwable) {
            false
        }
    }

    private suspend fun walletSubscribe(wallets: List<WalletEntity>): Boolean {
        if (wallets.isEmpty()) {
            return true
        }

        try {
            for (wallet in wallets) {
                settingsRepository.setPushWallet(wallet.id, true)
            }

            val firebaseToken = getFirebaseToken() ?: throw IllegalStateException("Firebase token not found")
            val accounts = wallets.map { it.accountId }
            val successful = api.pushSubscribe(
                locale = context.locale,
                firebaseToken = firebaseToken,
                deviceId = settingsRepository.installId,
                accounts = accounts,
            )

            if (!successful) {
                throw IllegalStateException("Failed to subscribe")
            }

            for (wallet in wallets) {
                val apps = dAppsRepository.getConnections(wallet.accountId, wallet.network)
                for ((app, connections) in apps) {
                    dAppPush(
                        wallet = wallet,
                        connections = connections,
                        commercial = dAppsRepository.isPushEnabled(wallet.accountId, wallet.network, app.url),
                        silent = true
                    )
                }
            }
            return true
        } catch (e: Throwable) {
            for (wallet in wallets) {
                settingsRepository.setPushWallet(wallet.id, false)
            }
            return false
        }
    }

    private suspend fun walletUnsubscribe(wallets: List<WalletEntity>, delete: Boolean): Boolean {
        if (wallets.isEmpty()) {
            return true
        }

        try {
            val accounts = wallets.map { it.accountId }
            val successful = api.pushUnsubscribe(
                deviceId = settingsRepository.installId,
                accounts = accounts,
            )
            if (!successful) {
                throw IllegalStateException("Failed to unsubscribe")
            }

            for (wallet in wallets) {
                settingsRepository.setPushWallet(wallet.id, false)
                if (!delete) {
                    val apps = dAppsRepository.getConnections(wallet.accountId, wallet.network)
                    for ((_, connections) in apps) {
                        dAppPush(
                            wallet = wallet,
                            connections = connections,
                            commercial = false,
                            silent = false
                        )
                    }
                }
            }
            return true
        } catch (e: Throwable) {
            for (wallet in wallets) {
                settingsRepository.setPushWallet(wallet.id, true)
            }
            return false
        }
    }

    suspend fun dAppPush(
        wallet: WalletEntity,
        connections: List<AppConnectEntity>,
        commercial: Boolean,
        silent: Boolean = settingsRepository.getPushWallet(wallet.id),
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (wallet.testnet) {
                throw IllegalStateException("Testnet wallet not supported")
            }
            val tonProof = getTonProof(wallet) ?: throw IllegalStateException("Ton proof not found")
            val firebaseToken = getFirebaseToken() ?: throw IllegalStateException("Firebase token not found")
            val tasks = mutableListOf<Deferred<Boolean>>()
            for (connection in connections) {
                tasks.add(async { dAppPush(wallet, tonProof, firebaseToken, connection, commercial, silent) })
            }
            tasks.map { it.await() }.all { it }
            true
        } catch (e: Throwable) {
            false
        }
    }

    private fun dAppPush(
        wallet: WalletEntity,
        tonProof: String,
        firebaseToken: String,
        connection: AppConnectEntity,
        commercial: Boolean,
        silent: Boolean,
    ) = api.pushTonconnectSubscribe(
        token = tonProof,
        appUrl = connection.appUrl.toString(),
        accountId = wallet.accountId,
        firebaseToken = firebaseToken,
        sessionId = connection.clientId,
        commercial = commercial,
        silent = silent
    )

    suspend fun dAppUnsubscribe(
        wallet: WalletEntity,
        connections: List<AppConnectEntity>
    ): Boolean = withContext(Dispatchers.IO) {
        if (wallet.testnet) {
            return@withContext false
        }
        val tonProof = getTonProof(wallet) ?: return@withContext false
        val firebaseToken = getFirebaseToken() ?: return@withContext false
        val tasks = mutableListOf<Deferred<Boolean>>()
        for (connection in connections) {
            tasks.add(async { dAppUnsubscribe(wallet, tonProof, firebaseToken, connection) })
        }
        tasks.map { it.await() }.all { it }
    }

    private fun dAppUnsubscribe(
        wallet: WalletEntity,
        tonProof: String,
        firebaseToken: String,
        connection: AppConnectEntity,
    ) = api.pushTonconnectUnsubscribe(
        token = tonProof,
        appUrl = connection.appUrl.toString(),
        accountId = wallet.accountId,
        firebaseToken = firebaseToken,
    )

    private suspend fun getFirebaseToken(): String? {
        return settingsRepository.firebaseToken ?: FirebasePush.requestToken()?.also {
            settingsRepository.firebaseToken = it
        }
    }

    private suspend fun getTonProof(wallet: WalletEntity): String? {
        return accountRepository.requestTonProofToken(wallet)
    }

    private fun isPushEnabled(wallet: WalletEntity): Boolean {
        return settingsRepository.getPushWallet(wallet.id)
    }

}