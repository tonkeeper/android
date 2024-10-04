package com.tonapps.tonkeeper.manager.push

import android.content.Context
import com.tonapps.extensions.locale
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PushManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val api: API,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val dAppsRepository: DAppsRepository,
) {

    enum class State {
        Enable, Disable, Delete
    }

    fun newFirebaseToken() {
        scope.launch(Dispatchers.IO) {
            val wallets = accountRepository.getWallets()
            wallets(wallets.filter { isPushEnabled(it) }, State.Enable)
            wallets(wallets.filter { !isPushEnabled(it) }, State.Disable)
        }
    }

    fun walletAsync(wallet: WalletEntity, state: State) {
        scope.launch { wallet(wallet, state) }
    }

    fun walletsAsync(wallets: List<WalletEntity>, state: State) {
        scope.launch { wallets(wallets, state) }
    }

    suspend fun wallet(wallet: WalletEntity, state: State) = wallets(listOf(wallet), state)

    suspend fun wallets(wallets: List<WalletEntity>, state: State): Boolean = withContext(Dispatchers.IO) {
        if (state == State.Enable) {
            walletSubscribe(wallets.filter { !it.testnet })
        } else {
            walletUnsubscribe(wallets, state == State.Delete)
        }
    }

    private suspend fun walletSubscribe(wallets: List<WalletEntity>): Boolean {
        if (wallets.isEmpty()) {
            return true
        }

        for (wallet in wallets) {
            settingsRepository.setPushWallet(wallet.id, true)
        }

        val firebaseToken = getFirebaseToken() ?: return false
        val accounts = wallets.map { it.accountId }
        val successful = api.pushSubscribe(
            locale = context.locale,
            firebaseToken = firebaseToken,
            deviceId = settingsRepository.installId,
            accounts = accounts,
        )
        if (successful) {
            for (wallet in wallets) {
                val apps = dAppsRepository.getConnections(wallet.accountId, wallet.testnet)
                for ((app, connections) in apps) {
                    dAppPush(
                        wallet = wallet,
                        connections = connections,
                        commercial = dAppsRepository.isPushEnabled(wallet.accountId, wallet.testnet, app.url),
                        silent = true
                    )
                }
            }
            return true
        } else {
            return false
        }
    }

    private suspend fun walletUnsubscribe(wallets: List<WalletEntity>, delete: Boolean): Boolean {
        if (wallets.isEmpty()) {
            return true
        }
        val accounts = wallets.map { it.accountId }
        val successful = api.pushUnsubscribe(
            deviceId = settingsRepository.installId,
            accounts = accounts,
        )
        if (successful) {
            for (wallet in wallets) {
                settingsRepository.setPushWallet(wallet.id, false)
                if (!delete) {
                    val apps = dAppsRepository.getConnections(wallet.accountId, wallet.testnet)
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
        } else {
            return false
        }
    }

    suspend fun dAppPush(
        wallet: WalletEntity,
        connections: List<AppConnectEntity>,
        commercial: Boolean,
        silent: Boolean = settingsRepository.getPushWallet(wallet.id),
    ): Boolean = withContext(Dispatchers.IO) {
        if (wallet.testnet) {
            return@withContext false
        }
        val tonProof = getTonProof(wallet) ?: return@withContext false
        val firebaseToken = getFirebaseToken() ?: return@withContext false
        val tasks = mutableListOf<Deferred<Boolean>>()
        for (connection in connections) {
            tasks.add(async { dAppPush(wallet, tonProof, firebaseToken, connection, commercial, silent) })
        }
        tasks.map { it.await() }.all { it }
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