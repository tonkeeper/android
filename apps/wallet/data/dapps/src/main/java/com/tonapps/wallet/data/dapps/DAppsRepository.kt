package com.tonapps.wallet.data.dapps

import android.net.Uri
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.chainkit.core.chain.model.account.Chain
import androidx.collection.ArrayMap
import androidx.core.net.toUri
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.extensions.map
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toParcel
import com.tonapps.extensions.withoutQuery
import com.tonapps.security.CryptoBox
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.core.recordException
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppConnectWithDetails
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.data.dapps.entities.AppNotificationsEntity
import com.tonapps.wallet.data.dapps.entities.AppPushEntity
import com.tonapps.wallet.data.dapps.entities.DappProvider
import com.tonapps.wallet.data.dapps.source.TonConnectPrefs
import com.tonapps.wallet.data.dapps.source.db.AppDao
import com.tonapps.wallet.data.dapps.source.db.AppRow
import com.tonapps.wallet.data.dapps.source.db.ConnectDao
import com.tonapps.wallet.data.dapps.source.db.ConnectEntity
import com.tonapps.wallet.data.dapps.source.db.NotificationDao
import com.tonapps.wallet.data.dapps.source.db.NotificationRow
import com.tonapps.wallet.data.dapps.wc.WcRepository
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.rn.data.RNTC
import com.tonapps.wallet.data.rn.data.RNTCApp
import com.tonapps.wallet.data.rn.data.RNTCApps
import com.tonapps.wallet.data.rn.data.RNTCConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DAppsRepository internal constructor(
    private val scope: CoroutineScope,
    private val rnLegacy: RNLegacy,
    private val api: API,
    private val appDao: AppDao,
    private val connectDao: ConnectDao,
    private val notificationDao: NotificationDao,
    private val tonConnectPrefs: TonConnectPrefs,
    private val wcRepository: WcRepository,
) {

    private val _connectionsFlow = MutableStateFlow<List<AppConnectEntity>?>(null)
    val connectionsFlow = _connectionsFlow.shareIn(scope, SharingStarted.Eagerly, 1).filterNotNull()

    // Raw-row-only deletes leave the projection unchanged, so _connectionsFlow conflates and never emits.
    private val _connectionsInvalidationFlow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _notificationsFlow = MutableStateFlow<List<AppNotificationsEntity>>(emptyList())
    val notificationsFlow = _notificationsFlow.asStateFlow()

    var lastEventId: Long
        get() = tonConnectPrefs.getLastEventId()
        set(value) {
            tonConnectPrefs.setLastEventId(value)
        }

    init {
        scope.launch(Dispatchers.IO) {
            try {
                backfillKeyPairsFromLegacyPrefs()

                if (rnLegacy.isRequestMigration()) {
                    migrationFromLegacy()
                }

                val connections = loadConnections()
                if (connections.isEmpty()) {
                    migrationFromLegacy()
                    _connectionsFlow.value = loadConnections()
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
        scope.launch(Dispatchers.IO) {
            notificationDao.insert(
                NotificationRow(
                    appUrl = body.dappUrl.withoutQuery.toString().removeSuffix("/"),
                    accountId = body.account.toRawAddress(),
                    body = body.toByteArray(),
                )
            )
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

    private suspend fun getPushes(accountId: String): AppNotificationsEntity = withContext(Dispatchers.IO) {
        val pushes = notificationDao.getByAccountId(accountId)
            .mapNotNull { it.body?.toParcel<AppPushEntity.Body>() }
        if (pushes.isEmpty()) {
            AppNotificationsEntity(accountId)
        } else {
            createAppNotifications(accountId, pushes)
        }
    }

    private suspend fun loadPushes(accountId: String, tonProof: String): AppNotificationsEntity {
        val pushes = api.getPushFromApps(tonProof, accountId).map { AppPushEntity.Body(it) }
        withContext(Dispatchers.IO) {
            notificationDao.replaceForAccount(
                accountId = accountId,
                rows = pushes.map { body ->
                    NotificationRow(
                        appUrl = body.dappUrl.withoutQuery.toString().removeSuffix("/"),
                        accountId = accountId,
                        body = body.toByteArray(),
                    )
                }
            )
        }
        return if (pushes.isEmpty()) {
            AppNotificationsEntity(accountId)
        } else {
            createAppNotifications(accountId, pushes)
        }
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
        network: TonNetwork
    ): ArrayMap<AppEntity, List<AppConnectEntity>> {
        val connections = (_connectionsFlow.value ?: emptyList()).filter {
            it.accountId == accountId && it.network == network
        }
        val map = connections.groupBy { it.appUrl.withoutQuery }
        val apps = getApps(map.keys.toList())
        val result = ArrayMap<AppEntity, List<AppConnectEntity>>()
        for (app in apps) {
            result[app] = map[app.url] ?: emptyList()
        }
        return result
    }

    suspend fun getConnections(): List<AppConnectEntity> = loadConnections()

    // Raw-row check so the disconnect confirmation is offered for rows the projection drops.
    suspend fun hasConnections(
        accountId: String,
        network: TonNetwork,
        appUrl: Uri? = null,
        type: AppConnectEntity.Type? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val targetHost = appUrl?.host?.lowercase()
        val targetPath = appUrl?.path?.trimEnd('/').orEmpty()
        connectDao.getTonConnectAll().any { row ->
            row.accountId == accountId &&
            (row.mode ?: TonNetwork.MAINNET.value) == network.value &&
            (type == null || row.type == type.value) &&
            (targetHost == null || matchesTarget(row.appUrl?.toUri(), targetHost, targetPath))
        }
    }

    // Unified view across TonConnect + WalletConnect rows joined with their app metadata,
    // scoped to a single wallet.
    // - TonConnect: filtered by account/mode at the DAO level; AppRow comes from the `app`
    //   table, joined by appUrl in memory.
    // - WalletConnect: filtered by walletId at the DAO level. The row carries no appUrl, so we
    //   synthesize AppRow from the live WC session metadata. Topics the SDK no longer reports
    //   as active drop out automatically — including stale DB rows where we missed the
    //   Disconnected event.
    fun getConnectionsWithDetails(
        walletId: String,
        accountId: String,
        mode: Int,
    ): List<AppConnectWithDetails> {
        val tonConnectRows = connectDao.getTonConnectByWallet(accountId, mode)
        val wcRows = connectDao.getWalletConnectByWalletId(walletId)
        val activeWcSessions = wcRepository.getActiveSessions().associateBy { it.topic }

        val tcAppUrls = tonConnectRows.mapNotNull { it.appUrl }.distinct()
        val appsByUrl = if (tcAppUrls.isEmpty()) {
            emptyMap()
        } else {
            appDao.getByUrls(tcAppUrls).associateBy { it.url }
        }

        val result = ArrayList<AppConnectWithDetails>(tonConnectRows.size + wcRows.size)

        for (row in tonConnectRows) {
            val app = appsByUrl[row.appUrl] ?: continue
            result.add(AppConnectWithDetails(row, app, chains = listOf(Chain.Ton.Mainnet.network.type)))
        }

        for (row in wcRows) {
            val topic = row.topic ?: continue
            val session = activeWcSessions[topic] ?: continue
            val app = AppRow(
                url = session.app.url,
                name = session.app.name,
                iconUrl = session.app.icon,
            )
            result.add(AppConnectWithDetails(row, app, chains = session.chains.toList()))
        }

        return result
    }

    fun connectionsWithDetailsFlow(
        walletId: String,
        accountId: String,
        mode: Int,
    ): Flow<List<AppConnectWithDetails>> = flow {
        emit(getConnectionsWithDetails(walletId, accountId, mode))
        merge(
            connectionsFlow.drop(1).map { },
            _connectionsInvalidationFlow.map { },
            wcRepository.sessions.map { },
        ).collect { emit(getConnectionsWithDetails(walletId, accountId, mode)) }
    }.flowOn(Dispatchers.IO)

    fun getLastAppRequestId(clientId: String): Long {
        return tonConnectPrefs.getLastAppRequestId(clientId)
    }

    fun setLastAppRequestId(clientId: String, requestId: Long) {
        tonConnectPrefs.setLastAppRequestId(clientId, requestId)
    }

    fun markOriginCleanup(profileName: String, host: String) {
        tonConnectPrefs.addPendingOriginCleanup(profileName, host)
    }

    fun hasOriginCleanup(profileName: String, host: String): Boolean {
        return tonConnectPrefs.hasPendingOriginCleanup(profileName, host)
    }

    fun consumeOriginCleanup(profileName: String, host: String): Boolean {
        return tonConnectPrefs.consumePendingOriginCleanup(profileName, host)
    }

    fun isPushEnabled(accountId: String, network: TonNetwork, appUrl: Uri): Boolean {
        return tonConnectPrefs.isPushEnabled(accountId, network, appUrl.withoutQuery)
    }

    fun setPushEnabled(accountId: String, network: TonNetwork, appUrl: Uri, enabled: Boolean): List<AppConnectEntity> {
        val otherConnections = mutableListOf<AppConnectEntity>()
        val accountConnections = mutableListOf<AppConnectEntity>()

        for (connection in (_connectionsFlow.value ?: return emptyList())) {
            if (connection.accountId == accountId && connection.network == network && connection.appUrl.withoutQuery == appUrl.withoutQuery) {
                accountConnections.add(connection.copy(pushEnabled = enabled))
            } else {
                otherConnections.add(connection.copy())
            }
        }

        if (accountConnections.isEmpty()) {
            return emptyList()
        }

        tonConnectPrefs.setPushEnabled(accountId, network, appUrl, enabled)
        _connectionsFlow.value = otherConnections + accountConnections
        return accountConnections
    }

    // webViewProfileName is null only for legacy migration, which predates any cleanup marker.
    suspend fun newConnect(
        connection: AppConnectEntity,
        webViewProfileName: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            connectDao.insert(
                ConnectEntity(
                    id = connection.clientId,
                    provider = DappProvider.TonConnect,
                    walletId = null,
                    appUrl = connection.appUrl.withoutQuery.toString().removeSuffix("/"),
                    createdAt = connection.timestamp,
                    accountId = connection.accountId,
                    mode = connection.network.value,
                    type = connection.type.value,
                    topic = null,
                    source = null,
                    keyPair = connection.keyPair.toByteArray(),
                )
            )
            tonConnectPrefs.clearLastEventId()

            updateConnectionsFlow { value ->
                value.add(connection.copy())
                value
            }

            // A live session must not be purged by a marker left behind by an earlier disconnect.
            val host = connection.appUrl.host?.lowercase()
            if (webViewProfileName != null && host != null) {
                consumeOriginCleanup(webViewProfileName, host)
            }
            true
        } catch (e: Throwable) {
            recordException(e)
            false
        }
    }

    suspend fun deleteConnect(connection: AppConnectEntity): Boolean = deleteConnect(connection.clientId)

    suspend fun deleteConnect(clientId: String): Boolean = withContext(Dispatchers.IO) {
        val removed = connectDao.deleteById(clientId) > 0
        if (!removed) {
            return@withContext false
        }
        updateConnectionsFlow { value ->
            value.removeIf { clientId == it.clientId }
            value
        }
        true
    }

    suspend fun deleteApp(
        accountId: String,
        network: TonNetwork,
        appUrl: Uri,
        type: AppConnectEntity.Type? = null
    ): List<AppConnectEntity> {
        val targetHost = appUrl.host?.lowercase() ?: return emptyList()
        val targetPath = appUrl.path.orEmpty().trimEnd('/')
        return deleteConnections { row ->
            row.accountId == accountId &&
            (row.mode ?: TonNetwork.MAINNET.value) == network.value &&
            matchesTarget(row.appUrl?.toUri(), targetHost, targetPath) &&
            (type == null || row.type == type.value)
        }
    }

    suspend fun getMatchingConnections(
        accountId: String,
        network: TonNetwork,
        appUrl: Uri,
        type: AppConnectEntity.Type?
    ): List<AppConnectEntity> {
        val targetHost = appUrl.host?.lowercase() ?: return emptyList()
        val targetPath = appUrl.path.orEmpty().trimEnd('/')
        return getConnections().filter { connection ->
            connection.accountId == accountId &&
            connection.network == network &&
            matchesTarget(connection.appUrl, targetHost, targetPath) &&
            (type == null || connection.type == type)
        }
    }

    // TODO: replace URL-based matching with a stable per-connection requestId (UUID or similar).
    // Origin-style rows (the norm) match the whole host; rows carrying a path (Telegram mini apps)
    // only match targets under that path, so a sibling's disconnect cannot delete them.
    private fun matchesTarget(storedUrl: Uri?, targetHost: String, targetPath: String): Boolean {
        if (storedUrl?.host?.lowercase() != targetHost) {
            return false
        }
        val storedPath = storedUrl.path.orEmpty().trimEnd('/')
        if (storedPath.isEmpty()) {
            return true
        }
        return targetPath == storedPath || targetPath.startsWith("$storedPath/")
    }

    suspend fun deleteApps(
        accountId: String,
        network: TonNetwork
    ): List<AppConnectEntity> {
        return deleteConnections { row ->
            row.accountId == accountId &&
            (row.mode ?: TonNetwork.MAINNET.value) == network.value
        }
    }

    // Matches raw rows: rows the projection drops (null key pair / unknown type) are still visible in the UI.
    private suspend fun deleteConnections(
        rawPredicate: (ConnectEntity) -> Boolean
    ): List<AppConnectEntity> {
        val ids = withContext(Dispatchers.IO) {
            connectDao.getTonConnectAll().filter(rawPredicate).map { it.id }
        }
        val removed = (_connectionsFlow.value ?: emptyList()).filter { it.clientId in ids }

        if (ids.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                connectDao.deleteByIds(ids)
            }

            updateConnectionsFlow { value ->
                value.removeIf { it.clientId in ids }
                value
            }
        }

        _connectionsInvalidationFlow.tryEmit(Unit)
        return removed
    }

    private fun updateConnectionsFlow(function: (MutableList<AppConnectEntity>) -> List<AppConnectEntity>) {
        _connectionsFlow.update {
            function((it ?: emptyList()).toMutableList()).toList()
        }
    }

    suspend fun getApps(url: Uri) = getApps(listOf(url))

    suspend fun getApps(urls: List<Uri>): List<AppEntity> {
        val apps = readApps(urls).toMutableList()
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

    suspend fun insertApp(app: AppEntity) = withContext(Dispatchers.IO) {
        try {
            appDao.insert(
                AppRow(
                    url = app.url.withoutQuery.toString().removeSuffix("/"),
                    name = app.name,
                    iconUrl = app.iconUrl,
                )
            )
            tonConnectPrefs.clearLastEventId()
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private suspend fun migrationFromLegacy() {
        try {
            val tcApps = rnLegacy.getTCApps()
            for (app in tcApps.mainnet) {
                migrationFromLegacy(app, TonNetwork.MAINNET)
            }
            for (apps in tcApps.testnet) {
                migrationFromLegacy(apps, TonNetwork.TESTNET)
            }
        } catch (e: Throwable) {
            recordException(e)
        }
    }

    suspend fun migrationFromLegacy(connections: RNTCApps, network: TonNetwork) {
        val accountId = connections.address.toRawAddress()
        for (legacyApp in connections.apps) {
            val newApp = AppEntity(
                url = legacyApp.url.toUri(),
                name = legacyApp.name,
                iconUrl = legacyApp.icon,
                empty = false,
            )
            insertApp(newApp)
            for (legacyConnections in legacyApp.connections) {
                val newConnection = AppConnectEntity(
                    accountId = accountId,
                    network = network,
                    clientId = legacyConnections.clientId,
                    type = if (legacyConnections.type == "remote") {
                        AppConnectEntity.Type.External
                    } else {
                        AppConnectEntity.Type.Internal
                    },
                    appUrl = newApp.url,
                    keyPair = legacyConnections.keyPair,
                    pushEnabled = legacyApp.notificationsEnabled,
                )
                newConnect(newConnection)
            }
        }
    }

    private suspend fun addToLegacy(connections: List<AppConnectEntity>) {
        val appUrls = connections.map { it.appUrl.withoutQuery }.distinct()
        val apps = getApps(appUrls)
        val appsMap = apps.associateBy { it.url }

        val (mainnetConnections, testnetConnections) = LegacyHelper.sortByNetworkAndAccount(connections)

        val data = RNTC(
            mainnet = addToLegacyCreateApps(mainnetConnections, TonNetwork.MAINNET, appsMap),
            testnet = addToLegacyCreateApps(testnetConnections, TonNetwork.TESTNET, appsMap)
        )
        rnLegacy.setTCApps(data)
    }

    private fun addToLegacyCreateApps(
        connectionsMap: ArrayMap<String, List<AppConnectEntity>>,
        network: TonNetwork,
        appsMap: Map<Uri, AppEntity>
    ): List<RNTCApps> {
        val legacyApps = mutableListOf<RNTCApps>()

        for ((accountId, connections) in connectionsMap) {
            val connectionsByAppUrls = LegacyHelper.sortByUrl(connections)
            val legacyAccountApps = mutableListOf<RNTCApp>()

            for ((appUrl, appUrlConnections) in connectionsByAppUrls) {
                val app = appsMap[appUrl] ?: continue
                val notificationsEnabled = isPushEnabled(
                    accountId = accountId,
                    network = network,
                    appUrl = appUrl
                )
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
                address = accountId.toUserFriendly(wallet = true, bounceable = true, testnet = network.isTestnet),
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

    private suspend fun loadConnections(): List<AppConnectEntity> = withContext(Dispatchers.IO) {
        connectDao.getTonConnectAll().mapNotNull(::toAppConnect)
    }

    private fun toAppConnect(row: ConnectEntity): AppConnectEntity? {
        val accountId = row.accountId ?: return null
        val mode = row.mode ?: TonNetwork.MAINNET.value
        val network = TonNetwork.entries.firstOrNull { it.value == mode } ?: return null
        val type = AppConnectEntity.Type.entries.firstOrNull { it.value == row.type } ?: return null
        val timestamp = row.createdAt ?: return null
        val appUrl = row.appUrl?.toUri()?.withoutQuery ?: return null
        val keyPair = row.keyPair?.toParcel<CryptoBox.KeyPair>() ?: return null

        return AppConnectEntity(
            accountId = accountId,
            network = network,
            clientId = row.id,
            type = type,
            appUrl = appUrl,
            keyPair = keyPair,
            timestamp = timestamp,
            pushEnabled = tonConnectPrefs.isPushEnabled(accountId, network, appUrl),
        )
    }

    // One-shot backfill: for upgrading users, copy keypairs that used to live in
    // EncryptedSharedPreferences into the connect row's `key_pair` column, then drop the
    // legacy prefs entry. New connections write straight to the row, so this is idempotent
    // and becomes a no-op once everyone is migrated.
    private fun backfillKeyPairsFromLegacyPrefs() {
        try {
            val rows = connectDao.getTonConnectMissingKeyPair()
            for (row in rows) {
                val accountId = row.accountId ?: continue
                val mode = row.mode ?: TonNetwork.MAINNET.value
                val network = TonNetwork.entries.firstOrNull { it.value == mode } ?: continue
                val bytes = tonConnectPrefs.consumeLegacyKeyPair(accountId, network, row.id)
                    ?: continue
                connectDao.updateKeyPair(row.id, bytes)
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private suspend fun readApps(urls: List<Uri>): List<AppEntity> = withContext(Dispatchers.IO) {
        if (urls.isEmpty()) {
            return@withContext emptyList()
        }
        val keys = urls.map { it.withoutQuery.toString() }
        appDao.getByUrls(keys).map { row ->
            AppEntity(
                url = (row.url).removeSuffix("/").toUri(),
                name = row.name.orEmpty(),
                iconUrl = row.iconUrl.orEmpty(),
                empty = false,
            )
        }
    }

    companion object {

        private val manifestPaths = arrayOf(
            "tonconnect-manifest.json",
            "manifest.json",
            "tcm.json"
        )

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
    }

}
