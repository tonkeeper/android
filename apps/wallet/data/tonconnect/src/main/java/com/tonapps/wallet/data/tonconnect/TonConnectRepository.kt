package com.tonapps.wallet.data.tonconnect

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.extensions.prefs
import com.tonapps.security.CryptoBox
import com.tonapps.security.hex
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.WalletProof
import com.tonapps.wallet.data.account.entities.ProofDomainEntity
import org.ton.crypto.base64
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.ProofEntity
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.tonconnect.entities.DConnectEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppItemEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppManifestEntity
import com.tonapps.wallet.data.tonconnect.entities.DConnectEntity.Type
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppAddressItemEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppEventSuccessEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppProofItemReplySuccess
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppReply
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppSuccessEntity
import com.tonapps.wallet.data.tonconnect.source.LocalDataSource
import com.tonapps.wallet.data.tonconnect.source.RemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd
import org.ton.block.StateInit

class TonConnectRepository(
    private val scope: CoroutineScope,
    private val context: Context,
    private val api: API,
    private val accountRepository: AccountRepository,
    private val rnLegacy: RNLegacy,
) {

    private val localDataSource = LocalDataSource(context)
    private val remoteDataSource = RemoteDataSource(api)
    private val prefs = context.prefs("tonconnect")
    private val events = EventsHelper(prefs, accountRepository, api)

    private val _connectionsFlow = MutableStateFlow<List<DConnectEntity>?>(null)
    val connectionsFlow = _connectionsFlow.asStateFlow().filterNotNull()

    val eventsFlow = events.flow(connectionsFlow).onEach {
        if (it.method == "disconnect") {
            disconnect(it.connect)
        }
    }.filterNotNull().flowOn(Dispatchers.IO).shareIn(scope, SharingStarted.Eagerly, 1)

    init {
        scope.launch(Dispatchers.IO) {
            if (rnLegacy.isRequestMigration()) {
                localDataSource.clearConnections()
                migrationFromRN()
            }

            val apps = localDataSource.getConnections()
            if (apps.isEmpty()) {
                migrationFromRN()
                _connectionsFlow.value = localDataSource.getConnections()
            } else {
                _connectionsFlow.value = apps
            }
        }
    }

    private suspend fun migrationFromRN() {
        val tcApps = rnLegacy.getJSONState("TCApps")
        val value = tcApps?.getJSONObject("connectedApps") ?: return
        value.optJSONObject("mainnet")?.let { migrationRN(it, false) }
        value.optJSONObject("testnet")?.let { migrationRN(it, true) }
    }

    private suspend fun migrationRN(value: JSONObject, testnet: Boolean) {
        for (key in value.keys()) {
            val address = key.toRawAddress()
            val wallet = accountRepository.getWalletByAccountId(address, testnet) ?: continue
            val json = value.getJSONObject(key)
            for (clientId in json.keys()) {
                try {
                    migrationRNApp(wallet, clientId, json.getJSONObject(clientId))
                } catch (ignored: Throwable) { }
            }
        }
    }

    private fun migrationRNApp(
        wallet: WalletEntity,
        clientId: String,
        json: JSONObject
    ) {
        val manifest = DAppManifestEntity(
            url = json.getString("url").removeSuffix("/"),
            name = json.getString("name"),
            iconUrl = json.getString("icon"),
            termsOfUseUrl = "",
            privacyPolicyUrl = "",
            source = "",
        )

        val notificationsEnabled = json.optBoolean("notificationsEnabled", false)
        val connections = json.optJSONArray("connections") ?: return
        if (connections.length() == 0) {
            return
        }
        val connection = connections.getJSONObject(connections.length() - 1)
        val sessionKeyPair = connection.optJSONObject("sessionKeyPair")
        val keyPair = if (sessionKeyPair == null) {
            CryptoBox.keyPair()
        } else {
            CryptoBox.KeyPair(
                publicKey = sessionKeyPair.getString("publicKey").hex(),
                privateKey = sessionKeyPair.getString("secretKey").hex(),
            )
        }

        val connect = DConnectEntity(
            walletId = wallet.id,
            accountId = wallet.accountId,
            testnet = wallet.testnet,
            clientId = connection.optString("clientSessionId", clientId),
            keyPair = keyPair,
            enablePush = notificationsEnabled,
            type = if (connection.optString("type") == "remote") {
                Type.External
            } else {
                Type.Internal
            },
            url = manifest.url,
            manifest = manifest,
        )

        localDataSource.addConnect(connect)
    }

    fun setPushEnabled(walletId: String, url: String, enabled: Boolean) {
        val connect = localDataSource.getConnect(walletId, url) ?: return
        if (connect.enablePush != enabled) {
            setPushEnabled(connect, enabled)
        }
    }

    fun setPushEnabled(connect: DConnectEntity, enabled: Boolean) {
        if (connect.enablePush != enabled) {
            val newConnect = connect.copy(enablePush = enabled)
            localDataSource.updateConnect(newConnect)
            _connectionsFlow.value = _connectionsFlow.value?.map {
                if (it.clientId == connect.clientId) newConnect else it
            }
        }
    }

    fun disconnect(connect: DConnectEntity) {
        localDataSource.deleteConnect(connect.walletId, connect.url)
        _connectionsFlow.value = _connectionsFlow.value?.filter { connect.clientId != it.clientId }
    }

    fun getApps(urls: List<String>, wallet: WalletEntity): List<DConnectEntity> {
        return urls.mapNotNull { getConnect(it, wallet) }.distinctBy { it.publicKeyHex }
    }

    fun getConnect(url: String, wallet: WalletEntity): DConnectEntity? {
        val apps = localDataSource.getConnections().filter { it.walletId == wallet.id }
        return apps.find {
            Uri.parse(it.url).host == Uri.parse(url).host
        }
    }

    suspend fun getLocalManifest(url: String): DAppManifestEntity? = withContext(Dispatchers.IO) {
        localDataSource.getManifest(url)
    }

    suspend fun getManifest(sourceUrl: String): DAppManifestEntity? = withContext(Dispatchers.IO) {
        try {
            val local = localDataSource.getManifest(sourceUrl)
            if (local == null) {
                val remote = remoteDataSource.loadManifest(sourceUrl)
                localDataSource.setManifest(sourceUrl, remote)
                remote
            } else {
                local
            }
        } catch (e: Throwable) {
            null
        }
    }

    private suspend fun newApp(
        wallet: WalletEntity,
        manifest: DAppManifestEntity,
        clientId: String,
        enablePush: Boolean,
        type: Type,
    ): DConnectEntity = withContext(Dispatchers.IO) {
        val keyPair = CryptoBox.keyPair()
        val connect = DConnectEntity(
            accountId = wallet.accountId,
            testnet = wallet.testnet,
            clientId = clientId,
            keyPair = keyPair,
            walletId = wallet.id,
            enablePush = enablePush,
            type = type,
            url = manifest.url,
            manifest = manifest,
        )
        localDataSource.addConnect(connect)
        val oldValue = _connectionsFlow.value ?: emptyList()
        _connectionsFlow.value = oldValue.plus(connect)
        connect
    }

    suspend fun send(
        connect: DConnectEntity,
        body: String,
    ) = withContext(Dispatchers.IO) {
        val encrypted = connect.encrypt(body)
        api.tonconnectSend(connect.publicKeyHex, connect.clientId, base64(encrypted))
    }

    suspend fun send(
        connect: DConnectEntity,
        body: JSONObject,
    ) = send(connect, body.toString())

    suspend fun sendError(
        requestId: String,
        connect: DConnectEntity,
        errorCode: Int,
        errorMessage: String
    ) {
        val error = JSONObject()
        error.put("code", errorCode)
        error.put("message", errorMessage)

        val json = JSONObject()
        json.put("id", requestId)
        json.put("error", error)

        send(connect, json.toString())
    }

    suspend fun send(
        requestId: String,
        connect: DConnectEntity,
        result: String
    ) {
        val data = DAppSuccessEntity(requestId, result)
        send(connect, data.toJSON())
    }

    private suspend fun unsubscribePush(
        wallet: WalletEntity,
        connect: DConnectEntity,
        firebaseToken: String
    ) {
        val proofToken = accountRepository.requestTonProofToken(wallet) ?: return
        val url = connect.url
        api.pushTonconnectUnsubscribe(
            token = proofToken,
            appUrl = url,
            accountId = wallet.address,
            firebaseToken = firebaseToken,
        )
    }

    private suspend fun subscribePush(
        wallet: WalletEntity,
        connect: DConnectEntity,
        firebaseToken: String
    ) {
        val proofToken = accountRepository.requestTonProofToken(wallet) ?: return
        val url = connect.url
        api.pushTonconnectSubscribe(
            token = proofToken,
            appUrl = url,
            accountId = wallet.address,
            firebaseToken = firebaseToken,
            sessionId = connect.clientId,
            commercial = true,
            silent = false,
        )
    }

    private suspend fun subscribePush(
        connect: DConnectEntity,
        firebaseToken: String
    ) {
        val wallet = accountRepository.getWalletById(connect.walletId) ?: return
        subscribePush(wallet, connect, firebaseToken)
    }

    suspend fun updatePushToken(firebaseToken: String) = withContext(Dispatchers.IO) {
        val connections = localDataSource.getConnections()
        for (connect in connections) {
            subscribePush(connect, firebaseToken)
        }
    }

    suspend fun connectLedger(
        wallet: WalletEntity,
        manifest: DAppManifestEntity,
        clientId: String,
        requestItems: List<DAppItemEntity>,
        firebaseToken: String?,
        type: Type,
        proofEntity: ProofEntity?
    ): DAppEventSuccessEntity = withContext(Dispatchers.IO) {
        val enablePush = firebaseToken != null
        val app = newApp(
            wallet = wallet,
            manifest = manifest,
            clientId = clientId,
            enablePush = enablePush,
            type = type
        )
        val items = createLedgerItems(wallet, requestItems, proofEntity)
        val res = DAppEventSuccessEntity(items)
        send(app, res.toJSON())
        firebaseToken?.let {
            subscribePush(wallet, app, it)
        }
        res.copy()
    }

    suspend fun connect(
        wallet: WalletEntity,
        privateKey: PrivateKeyEd25519,
        manifest: DAppManifestEntity,
        clientId: String,
        requestItems: List<DAppItemEntity>,
        firebaseToken: String?,
        type: Type,
    ): DAppEventSuccessEntity = withContext(Dispatchers.IO) {
        val enablePush = firebaseToken != null
        val app = newApp(
            wallet = wallet,
            manifest = manifest,
            clientId = clientId,
            enablePush = enablePush,
            type = type
        )
        val items = createItems(app, wallet, privateKey, requestItems)
        val res = DAppEventSuccessEntity(items)
        send(app, res.toJSON())
        firebaseToken?.let {
            subscribePush(wallet, app, it)
        }
        res.copy()
    }

    suspend fun autoConnect(
        wallet: WalletEntity,
    ): DAppEventSuccessEntity = withContext(Dispatchers.IO) {
        val items = mutableListOf<DAppReply>()
        items.add(createAddressItem(
            accountId = wallet.accountId,
            testnet = wallet.testnet,
            publicKey = wallet.publicKey,
            stateInit = wallet.contract.stateInit
        ))
        DAppEventSuccessEntity(items)
    }

    private fun createLedgerItems(
        wallet: WalletEntity,
        items: List<DAppItemEntity>,
        proof: ProofEntity?
    ): List<DAppReply> {
        val result = mutableListOf<DAppReply>()
        for (requestItem in items) {
            if (requestItem.name == DAppItemEntity.TON_ADDR) {
                result.add(createAddressItem(
                    accountId = wallet.accountId,
                    testnet = wallet.testnet,
                    publicKey = wallet.publicKey,
                    stateInit = wallet.contract.stateInit
                ))
            } else if (requestItem.name == DAppItemEntity.TON_PROOF && proof != null) {
                result.add(DAppProofItemReplySuccess(proof = proof))
            }
        }
        return result
    }

    private fun createItems(
        connect: DConnectEntity,
        wallet: WalletEntity,
        privateKey: PrivateKeyEd25519,
        items: List<DAppItemEntity>
    ): List<DAppReply> {
        val result = mutableListOf<DAppReply>()
        for (requestItem in items) {
            if (requestItem.name == DAppItemEntity.TON_ADDR) {
                result.add(createAddressItem(
                    accountId = wallet.accountId,
                    testnet = wallet.testnet,
                    publicKey = wallet.publicKey,
                    stateInit = wallet.contract.stateInit
                ))
            } else if (requestItem.name == DAppItemEntity.TON_PROOF) {
                result.add(createProofItem(
                    payload = requestItem.payload ?: "",
                    domain = connect.domain,
                    address = wallet.contract.address,
                    privateWalletKey = privateKey,
                    stateInit = wallet.contract.getStateCell().base64()
                ))
            }
        }
        return result
    }

    private fun createProofItem(
        payload: String,
        domain: ProofDomainEntity,
        address: AddrStd,
        privateWalletKey: PrivateKeyEd25519,
        stateInit: String,
    ): DAppProofItemReplySuccess {
        val proof = WalletProof.sign(
            address,
            privateWalletKey,
            payload,
            domain,
            stateInit
        )
        return DAppProofItemReplySuccess(proof = proof)
    }

    private fun createAddressItem(
        accountId: String,
        testnet: Boolean,
        publicKey: PublicKeyEd25519,
        stateInit: StateInit
    ): DAppAddressItemEntity {
        return DAppAddressItemEntity(
            address = accountId,
            network = if (testnet) TonNetwork.TESTNET else TonNetwork.MAINNET,
            walletStateInit = stateInit,
            publicKey = publicKey
        )
    }
}