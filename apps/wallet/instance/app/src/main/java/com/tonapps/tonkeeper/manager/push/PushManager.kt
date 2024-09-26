package com.tonapps.tonkeeper.manager.push

import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

class PushManager(
    private val scope: CoroutineScope,
    private val api: API,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository
) {

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

    suspend fun dAppPush(
        wallet: WalletEntity,
        connections: List<AppConnectEntity>,
        enabled: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (wallet.testnet) {
            return@withContext false
        }
        val tonProof = getTonProof(wallet) ?: return@withContext false
        val firebaseToken = getFirebaseToken() ?: return@withContext false
        val tasks = mutableListOf<Deferred<Boolean>>()
        for (connection in connections) {
            tasks.add(async { dAppPush(wallet, tonProof, firebaseToken, connection, enabled) })
        }
        tasks.map { it.await() }.all { it }
    }

    private fun dAppPush(
        wallet: WalletEntity,
        tonProof: String,
        firebaseToken: String,
        connection: AppConnectEntity,
        enabled: Boolean
    ) = api.pushTonconnectSubscribe(
        token = tonProof,
        appUrl = connection.appUrl.toString(),
        accountId = wallet.accountId,
        firebaseToken = firebaseToken,
        sessionId = connection.clientId,
        commercial = enabled,
        silent = isPushEnabled(wallet)
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

}