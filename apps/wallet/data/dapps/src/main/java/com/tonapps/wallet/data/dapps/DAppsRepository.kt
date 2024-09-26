package com.tonapps.wallet.data.dapps

import android.content.Context
import android.net.Uri
import androidx.collection.ArrayMap
import androidx.core.net.toUri
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.data.dapps.source.DatabaseSource
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.rn.data.RNTC
import com.tonapps.wallet.data.rn.data.RNTCApp
import com.tonapps.wallet.data.rn.data.RNTCApps
import com.tonapps.wallet.data.rn.data.RNTCConnection
import com.tonapps.wallet.data.rn.data.RNTCKeyPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DAppsRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val rnLegacy: RNLegacy,
) {

    private val _connectionsFlow = MutableStateFlow<List<AppConnectEntity>?>(null)
    val connectionsFlow = _connectionsFlow.shareIn(scope, SharingStarted.Eagerly, 1).filterNotNull()

    private val database = DatabaseSource(context)

    var lastEventId: Long
        get() = database.getLastEventId()
        set(value) {
            database.setLastEventId(value)
        }

    init {
        scope.launch(Dispatchers.IO) {
            if (rnLegacy.isRequestMigration()) {
                database.clearConnections()
                migrationFromLegacy()
            }

            val connections = database.getConnections()
            if (connections.isEmpty()) {
                migrationFromLegacy()
                _connectionsFlow.value = database.getConnections()
            } else {
                _connectionsFlow.value = connections
            }
        }

        connectionsFlow.drop(1).onEach {
            addToLegacy(it.toList())
        }.flowOn(Dispatchers.IO).launchIn(scope)
    }

    fun getLastAppRequestId(clientId: String): Long {
        return database.getLastAppRequestId(clientId)
    }

    fun setLastAppRequestId(clientId: String, requestId: Long) {
        database.setLastAppRequestId(clientId, requestId)
    }

    fun isPushEnabled(accountId: String, testnet: Boolean, appUrl: Uri): Boolean {
        return database.isPushEnabled(accountId, testnet, appUrl)
    }

    fun setPushEnabled(accountId: String, testnet: Boolean, appUrl: Uri, enabled: Boolean): List<AppConnectEntity> {
        val otherConnections = mutableListOf<AppConnectEntity>()
        val accountConnections = mutableListOf<AppConnectEntity>()

        for (connection in (_connectionsFlow.value ?: return emptyList())) {
            if (connection.accountId == accountId && connection.testnet == testnet && connection.appUrl == appUrl) {
                accountConnections.add(connection.copy(pushEnabled = enabled))
            } else {
                otherConnections.add(connection.copy())
            }
        }

        if (accountConnections.isEmpty()) {
            return emptyList()
        }

        database.setPushEnabled(accountId, testnet, appUrl, enabled)
        _connectionsFlow.value = otherConnections + accountConnections
        return accountConnections
    }

    suspend fun newConnect(connection: AppConnectEntity): Boolean {
        try {
            database.insertConnection(connection)
            updateConnectionsFlow { value ->
                value.add(connection.copy())
                value
            }
            return true
        } catch (e: Throwable) {
            return false
        }
    }

    suspend fun deleteConnect(connection: AppConnectEntity): Boolean {
        if (!database.deleteConnect(connection)) {
            return false
        }
        updateConnectionsFlow { value ->
            value.removeIf { connection.clientId == it.clientId }
            value
        }
        return true
    }

    suspend fun deleteApp(
        accountId: String,
        testnet: Boolean,
        appUrl: Uri,
        type: AppConnectEntity.Type? = null
    ): List<AppConnectEntity> {
        if (type == null) {
            val predicate: (AppConnectEntity) -> Boolean = {
                it.accountId == accountId && it.testnet == testnet && it.appUrl == appUrl
            }

            return deleteConnections(predicate)
        } else {
            val predicate: (AppConnectEntity) -> Boolean = {
                it.accountId == accountId && it.testnet == testnet && it.appUrl == appUrl && it.type == type
            }

            return deleteConnections(predicate)
        }
    }

    suspend fun deleteApps(
        accountId: String,
        testnet: Boolean
    ): List<AppConnectEntity> {
        val predicate: (AppConnectEntity) -> Boolean = {
            it.accountId == accountId && it.testnet == testnet
        }
        return deleteConnections(predicate)
    }

    private suspend fun deleteConnections(
        predicate: (AppConnectEntity) -> Boolean
    ): List<AppConnectEntity> {
        val connections = (_connectionsFlow.value ?: emptyList()).filter(predicate)

        updateConnectionsFlow { value ->
            value.removeIf(predicate)
            value
        }

        for (connection in connections) {
            database.deleteConnect(connection)
        }
        return connections
    }

    private fun updateConnectionsFlow(function: (MutableList<AppConnectEntity>) -> List<AppConnectEntity>) {
        _connectionsFlow.update {
            function((it ?: emptyList()).toMutableList()).toList()
        }
    }

    suspend fun getApps(urls: List<Uri>): List<AppEntity> {
        return database.getApps(urls)
    }

    suspend fun getApp(url: Uri?): AppEntity? {
        if (url == null) {
            return null
        }
        return database.getApp(url)
    }

    suspend fun insertApp(app: AppEntity) {
        database.insertApp(app)
    }

    private suspend fun migrationFromLegacy() {
        try {
            val tcApps = rnLegacy.getTCApps()
            for (app in tcApps.mainnet) {
                migrationFromLegacy(app, false)
            }
            for (apps in tcApps.testnet) {
                migrationFromLegacy(apps, true)
            }
        } catch (ignored: Throwable) { }
    }

    private suspend fun migrationFromLegacy(connections: RNTCApps, testnet: Boolean) {
        val accountId = connections.address.toRawAddress()
        for (legacyApp in connections.apps) {
            val newApp = AppEntity(
                url = legacyApp.url.toUri(),
                name = legacyApp.name,
                iconUrl = legacyApp.icon,
                empty = false,
            )
            database.insertApp(newApp)
            for (legacyConnections in legacyApp.connections) {
                val newConnection = AppConnectEntity(
                    accountId = accountId,
                    testnet = testnet,
                    clientId = legacyConnections.clientId,
                    type = if (legacyConnections.type == "remote") AppConnectEntity.Type.External else AppConnectEntity.Type.Internal,
                    appUrl = newApp.url,
                    keyPair = legacyConnections.keyPair,
                    proofSignature = null,
                    proofPayload = null,
                    pushEnabled = legacyApp.notificationsEnabled,
                )
                database.insertConnection(newConnection)
            }
        }
    }

    private suspend fun addToLegacy(connections: List<AppConnectEntity>) {
        val appUrls = connections.map { it.appUrl }.distinct()
        val apps = getApps(appUrls)
        val appsMap = apps.associateBy { it.url }

        val (mainnetConnections, testnetConnections) = LegacyHelper.sortByNetworkAndAccount(connections)

        addToLegacyCreateApps(testnetConnections, true, appsMap)

        val data = RNTC(
            mainnet = addToLegacyCreateApps(mainnetConnections, false, appsMap),
            testnet = addToLegacyCreateApps(testnetConnections, true, appsMap)
        )
        rnLegacy.setTCApps(data)
    }

    private fun addToLegacyCreateApps(
        connectionsMap: ArrayMap<String, List<AppConnectEntity>>,
        testnet: Boolean,
        appsMap: Map<Uri, AppEntity>
    ): List<RNTCApps> {
        val legacyApps = mutableListOf<RNTCApps>()

        for ((accountId, connections) in connectionsMap) {
            val connectionsByAppUrls = LegacyHelper.sortByUrl(connections)
            val legacyAccountApps = mutableListOf<RNTCApp>()

            for ((appUrl, appUrlConnections) in connectionsByAppUrls) {
                val app = appsMap[appUrl] ?: continue
                val notificationsEnabled = isPushEnabled(accountId, testnet, appUrl)
                val legacyConnections = mutableListOf<RNTCConnection>()
                for (appUrlConnection in appUrlConnections) {
                    legacyConnections.add(LegacyHelper.createConnection(appUrlConnection))
                }
                legacyAccountApps.add(RNTCApp(
                    name = app.name,
                    url = app.url.toString(),
                    icon = app.iconUrl,
                    notificationsEnabled = notificationsEnabled,
                    connections = legacyConnections.toList()
                ))
            }

            if (legacyAccountApps.isEmpty()) {
                continue
            }

            legacyApps.add(RNTCApps(
                address = accountId.toUserFriendly(wallet = true, bounceable = true, testnet = testnet),
                apps = legacyAccountApps
            ))
        }

        return legacyApps.toList()
    }


    companion object {

        private val manifestPaths = arrayOf(
            "tonconnect-manifest.json",
            "manifest.json",
            "tcm.json"
        )
    }

}