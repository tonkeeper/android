package com.tonapps.wallet.data.dapps

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.collection.ArrayMap
import androidx.core.net.toUri
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.extensions.map
import com.tonapps.extensions.withoutQuery
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.core.recordException
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.data.dapps.entities.AppNotificationsEntity
import com.tonapps.wallet.data.dapps.entities.AppPushEntity
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
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class DAppsRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val rnLegacy: RNLegacy,
    private val api: API,
) {

    private val _connectionsFlow = MutableStateFlow<List<AppConnectEntity>?>(null)
    val connectionsFlow = _connectionsFlow.shareIn(scope, SharingStarted.Eagerly, 1).filterNotNull()

    private val _notificationsFlow = MutableStateFlow<List<AppNotificationsEntity>>(emptyList())
    val notificationsFlow = _notificationsFlow.asStateFlow()

    private val database: DatabaseSource by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DatabaseSource(context)
    }

    var lastEventId: Long
        get() = database.getLastEventId()
        set(value) {
            database.setLastEventId(value)
        }

    init {
        scope.launch(Dispatchers.IO) {
            try {
                if (rnLegacy.isRequestMigration()) {
                    migrationFromLegacy()
                }

                val connections = database.getConnections()
                if (connections.isEmpty()) {
                    migrationFromLegacy()
                    _connectionsFlow.value = database.getConnections()
                } else {
                    _connectionsFlow.value = connections
                }
            } catch (e: Throwable) {
                FirebaseCrashlytics.getInstance().recordException(e)
                _connectionsFlow.value = emptyList()
            }
        }

        connectionsFlow.drop(1).onEach {
            addToLegacy(it.toList())
        }.flowOn(Dispatchers.IO).launchIn(scope)
    }

    suspend fun refreshPushes(
        accountId: String,
        tonProof: String?
    ) = withContext(Dispatchers.IO) {
        refreshLocalPushes(accountId)
        tonProof?.let {
            val remote = loadPushes(accountId, it)
            setAppNotifications(remote)
        }
    }

    private suspend fun refreshLocalPushes(accountId: String) {
        val local = getPushes(accountId)
        if (!local.isEmpty) {
            setAppNotifications(local)
        }
    }

    fun insertDAppNotification(body: AppPushEntity.Body) {
        scope.launch {
            database.insertNotification(body)
            refreshLocalPushes(body.account.toRawAddress())
        }
    }

    private fun setAppNotifications(entity: AppNotificationsEntity) {
        val values = _notificationsFlow.value.toMutableList()
        val index = values.indexOfFirst { it.accountId == entity.accountId }
        if (index == -1) {
            values.add(entity)
        } else {
            values[index] = entity
        }
        _notificationsFlow.value = values
    }

    private suspend fun getPushes(accountId: String): AppNotificationsEntity {
        val pushes = database.getNotifications(accountId)
        if (pushes.isEmpty()) {
            return AppNotificationsEntity(accountId)
        }
        return createAppNotifications(accountId, pushes)
    }

    private suspend fun loadPushes(accountId: String, tonProof: String): AppNotificationsEntity {
        val pushes = api.getPushFromApps(tonProof, accountId).map { AppPushEntity.Body(it) }
        database.insertNotifications(accountId, pushes)
        if (pushes.isEmpty()) {
            return AppNotificationsEntity(accountId)
        }
        return createAppNotifications(accountId, pushes)
    }

    private suspend fun createAppNotifications(
        accountId: String,
        pushes: List<AppPushEntity.Body>
    ): AppNotificationsEntity {
        val apps = getApps(pushes.map { it.dappUrl })

        val list = mutableListOf<AppPushEntity>()
        for (push in pushes) {
            val app = apps.firstOrNull { it.url == push.dappUrl } ?: continue
            list.add(AppPushEntity(app, push))
        }

        return AppNotificationsEntity(accountId, list.toList())
    }

    suspend fun getConnections(
        accountId: String,
        testnet: Boolean
    ): ArrayMap<AppEntity, List<AppConnectEntity>> {
        val connections = (_connectionsFlow.value ?: emptyList()).filter {
            it.accountId == accountId && it.testnet == testnet
        }
        val map = connections.groupBy { it.appUrl.withoutQuery }
        val apps = getApps(map.keys.toList())
        val result = ArrayMap<AppEntity, List<AppConnectEntity>>()
        for (app in apps) {
            result[app] = map[app.url] ?: emptyList()
        }
        return result
    }

    suspend fun getConnections(): List<AppConnectEntity> {
        return database.getConnections()
    }

    fun getLastAppRequestId(clientId: String): Long {
        return database.getLastAppRequestId(clientId)
    }

    fun setLastAppRequestId(clientId: String, requestId: Long) {
        database.setLastAppRequestId(clientId, requestId)
    }

    fun isPushEnabled(accountId: String, testnet: Boolean, appUrl: Uri): Boolean {
        return database.isPushEnabled(accountId, testnet, appUrl.withoutQuery)
    }

    fun setPushEnabled(accountId: String, testnet: Boolean, appUrl: Uri, enabled: Boolean): List<AppConnectEntity> {
        val otherConnections = mutableListOf<AppConnectEntity>()
        val accountConnections = mutableListOf<AppConnectEntity>()

        for (connection in (_connectionsFlow.value ?: return emptyList())) {
            if (connection.accountId == accountId && connection.testnet == testnet && connection.appUrl.withoutQuery == appUrl.withoutQuery) {
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
            recordException(e)
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
                it.accountId == accountId && it.testnet == testnet && it.appUrl.withoutQuery == appUrl.withoutQuery
            }

            return deleteConnections(predicate)
        } else {
            val predicate: (AppConnectEntity) -> Boolean = {
                it.accountId == accountId && it.testnet == testnet && it.appUrl.withoutQuery == appUrl.withoutQuery && it.type == type
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

    suspend fun getApps(url: Uri) = getApps(listOf(url))

    suspend fun getApps(urls: List<Uri>): List<AppEntity> {
        val apps = database.getApps(urls).toMutableList()
        val notFoundApps = urls.filter { url -> apps.none { it.host == url.host } }
        if (notFoundApps.isNotEmpty()) {
            for (url in notFoundApps) {
                val app = resolveAppByHost(url)
                if (!app.empty) {
                    insertApp(app)
                }
                apps.add(app)
            }
        }
        return apps
    }

    suspend fun getApp(url: Uri): AppEntity {
        return getApps(listOf(url)).firstOrNull() ?: resolveAppByHost(url)
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
        } catch (e: Throwable) {
            recordException(e)
        }
    }

    suspend fun migrationFromLegacy(connections: RNTCApps, testnet: Boolean) {
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
        val appUrls = connections.map { it.appUrl.withoutQuery }.distinct()
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

    private suspend fun resolveAppByHost(url: Uri): AppEntity = withContext(Dispatchers.IO) {
        val host = url.host ?: return@withContext emptyApp(url)
        for (path in manifestPaths) {
            val manifestUrl = "https://$host/$path"
            try {
                return@withContext AppEntity(api.get(manifestUrl))
            } catch (e: Throwable) {
                FirebaseCrashlytics.getInstance().recordException(e)
                continue
            }
        }
        emptyApp(url)
    }

    private suspend fun emptyApp(url: Uri): AppEntity {
        val domain = url.host ?: "unknown"
        var name = api.getPageTitle(url.toString()).ifBlank { domain }
        name = fixAppTitle(name)
        return AppEntity(
            url = url,
            name = name.trim(),
            iconUrl = "https://$domain/favicon.ico",
            empty = true
        )
    }

    companion object {

        fun fixAppTitle(value: String): String {
            var name = value.trim()
            if (name.contains(":")) {
                name = name.substringBefore(":")
            } else if (name.contains("-")) {
                name = name.substringBefore("-")
            } else if (name.contains("|")) {
                name = name.substringBefore("|")
            } else if (name.contains("<")) {
                name = name.substringBefore("|")
            }
            if (name.length > 50) {
                name = name.substring(0, 50)
            }
            return name
        }

        private val manifestPaths = arrayOf(
            "tonconnect-manifest.json",
            "manifest.json",
            "tcm.json"
        )
    }

}