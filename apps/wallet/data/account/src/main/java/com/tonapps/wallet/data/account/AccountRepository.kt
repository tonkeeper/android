package com.tonapps.wallet.data.account

import android.content.Context
import android.util.Log
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.extensions.isMainVersion
import com.tonapps.ledger.ton.LedgerAccount
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.MessageBodyEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.entities.WalletEvent
import com.tonapps.wallet.data.account.source.DatabaseSource
import com.tonapps.wallet.data.account.source.StorageSource
import com.tonapps.wallet.data.account.source.VaultSource
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.rn.data.RNLedger
import com.tonapps.wallet.data.rn.data.RNWallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.RawValue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import org.ton.mnemonic.Mnemonic
import java.util.UUID

class AccountRepository(
    context: Context,
    private val api: API,
    private val rnLegacy: RNLegacy,
) {

    private companion object {
        private fun newWalletId(): String {
            return UUID.randomUUID().toString()
        }

        private fun createLabelName(
            name: String,
            suffix: String,
            size: Int
        ): String {
            if (size == 1) {
                return name
            }
            return "$name $suffix"
        }
    }

    sealed class SelectedState {
        data object Initialization : SelectedState()
        data object Empty : SelectedState()
        data class Wallet(val wallet: WalletEntity) : SelectedState()
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val database = DatabaseSource(context, scope)
    private val storageSource = StorageSource(context)
    private val vaultSource = VaultSource(context)
    private val migrationHelper = RNMigrationHelper(rnLegacy)

    private val _selectedStateFlow = MutableStateFlow<SelectedState>(SelectedState.Initialization)
    val selectedStateFlow = _selectedStateFlow.stateIn(scope, SharingStarted.Eagerly,
        SelectedState.Initialization
    )
    val selectedWalletFlow = selectedStateFlow.filterNotNull().filterIsInstance<SelectedState.Wallet>().map {
        it.wallet
    }.shareIn(scope, SharingStarted.Eagerly, 1).distinctUntilChanged()

    val selectedId: String?
        get() = (selectedStateFlow.value as? SelectedState.Wallet)?.wallet?.id

    @OptIn(ExperimentalCoroutinesApi::class)
    val realtimeEventsFlow = selectedWalletFlow.flatMapLatest { wallet ->
        api.accountEvents(wallet.accountId, wallet.testnet).map { event ->
            val isBoc = event.json.has("boc")
            if (isBoc) {
                WalletEvent.Boc(wallet, event.json)
            } else {
                WalletEvent.Transaction(wallet, event.json)
            }
        }
    }.flowOn(Dispatchers.IO).shareIn(scope, SharingStarted.Eagerly, 1)

    init {
        scope.launch(Dispatchers.IO) {
            if (rnLegacy.isRequestMainMigration()) {
                database.clearAccounts()
                storageSource.clear()
                migrationFromRN()
                rnLegacy.setWalletMigrated()
            }

            val selectedId = storageSource.getSelectedId()
            setSelectedWallet(selectedId)
        }
    }

    suspend fun importPrivateKeysFromRNLegacy(passcode: String) = withContext(Dispatchers.IO) {
        val list = migrationHelper.loadSecureStore(passcode)
        if (list.isEmpty()) {
            throw Exception("Empty list")
        }
        for (mnemonic in list) {
            vaultSource.addMnemonic(mnemonic.mnemonic.split(" "))
        }
    }

    private suspend fun migrationFromRN() = withContext(Dispatchers.IO) {
        val (selectedId, wallets) = migrationHelper.loadLegacy()
        if (wallets.isEmpty()) {
            _selectedStateFlow.value = SelectedState.Empty
        } else {
            database.insertAccounts(wallets)
            for (wallet in wallets) {
                val token = rnLegacy.getTonProof(wallet.id) ?: continue
                storageSource.setTonProofToken(wallet.id, token)
            }
            setSelectedWallet(selectedId)
        }
    }

    private suspend fun addWalletToRN(wallet: WalletEntity) {
        val type = when (wallet.type) {
            Wallet.Type.Default -> RNWallet.Type.Regular
            Wallet.Type.Watch -> RNWallet.Type.WatchOnly
            Wallet.Type.Lockup -> RNWallet.Type.Lockup
            Wallet.Type.SignerQR -> RNWallet.Type.Signer
            Wallet.Type.Signer -> RNWallet.Type.SignerDeeplink
            Wallet.Type.Ledger -> RNWallet.Type.Ledger
            else -> RNWallet.Type.Regular
        }

        var emoji = wallet.label.emoji.toString().removePrefix("custom_")
        val rnCustomIcon = RNWallet.icons[emoji]
        if (rnCustomIcon != null) {
            emoji = rnCustomIcon
        }

        val rnWallet = RNWallet(
            name = wallet.label.accountName,
            color = RNWallet.resolveColor(wallet.label.color),
            emoji = emoji,
            identifier = wallet.id,
            pubkey = wallet.publicKey.hex(),
            network = if (wallet.testnet) RNWallet.Network.Testnet else RNWallet.Network.Mainnet,
            type = type,
            version = wallet.version.title,
            workchain = wallet.contract.workchain,
            allowedDestinations = null,
            configPubKey = null,
            ledger = wallet.ledger?.let {
                RNLedger(
                    deviceId = it.deviceId,
                    accountIndex = it.accountIndex
                )
            },
        )
        rnLegacy.addWallet(rnWallet)
    }

    fun editLabel(name: String, emoji: CharSequence, color: Int) {
        scope.launch(scope.coroutineContext) {
            val wallet = getSelectedWallet() ?: return@launch
            val newLabel = Wallet.Label(name, emoji, color)
            _selectedStateFlow.value = SelectedState.Wallet(wallet.copy(label = newLabel))
            database.editAccount(wallet.id, Wallet.Label(name, emoji, color))
            rnLegacy.edit(wallet.id, name, emoji.toString(), color)
        }
    }

    suspend fun requestTonProofToken(id: String): String? = withContext(scope.coroutineContext) {
        val token = storageSource.getTonProofToken(id)
        if (token != null) {
            return@withContext token
        }
        val wallet = database.getAccount(id) ?: return@withContext null
        if (!wallet.hasPrivateKey) {
            return@withContext null
        }
        val tonProofToken = createTonProofToken(wallet) ?: return@withContext null
        saveTonProof(id, tonProofToken)
        tonProofToken
    }

    private suspend fun saveTonProof(id: String, token: String) = withContext(Dispatchers.IO) {
        storageSource.setTonProofToken(id, token)
        rnLegacy.setTonProof(id, token)
    }

    suspend fun getWallets() = database.getAccounts()

    suspend fun getMnemonic(id: String): Array<String>? {
        val wallet = database.getAccount(id) ?: return null
        return vaultSource.getMnemonic(wallet.publicKey)
    }

    suspend fun getPrivateKey(id: String): PrivateKeyEd25519 {
        val wallet = database.getAccount(id) ?: return EmptyPrivateKeyEd25519
        return vaultSource.getPrivateKey(wallet.publicKey) ?: EmptyPrivateKeyEd25519
    }

    suspend fun pairLedger(
        label: Wallet.Label,
        ledgerAccounts: List<LedgerAccount>,
        deviceId: String,
    ): List<WalletEntity> {
        val list = mutableListOf<WalletEntity>()
        for ((index, account) in ledgerAccounts.withIndex()) {
            val entity = WalletEntity(
                id = newWalletId(),
                publicKey = account.publicKey,
                type = Wallet.Type.Ledger,
                version = WalletVersion.V4R2,
                label = label.copy(
                    accountName = createLabelName(label.accountName, index.toString(), ledgerAccounts.size),
                ),
                ledger = WalletEntity.Ledger(
                    deviceId = deviceId,
                    accountIndex = account.path.index
                )
            )
            list.add(entity)
        }

        insertWallets(list)
        return list.toList()
    }

    suspend fun pairSigner(
        label: Wallet.Label,
        publicKey: PublicKeyEd25519,
        versions: List<WalletVersion>,
        qr: Boolean,
    ): List<WalletEntity> {
        val type = if (qr) Wallet.Type.SignerQR else Wallet.Type.SignerQR
        return addWallet(label, publicKey, versions, type)
    }

    suspend fun importWallet(
        label: Wallet.Label,
        mnemonic: List<String>,
        versions: List<WalletVersion>,
        testnet: Boolean,
    ): List<WalletEntity> {
        val publicKey = vaultSource.addMnemonic(mnemonic)
        val type = if (testnet) Wallet.Type.Testnet else Wallet.Type.Default
        return addWallet(label, publicKey, versions, type)
    }

    suspend fun addWallet(
        label: Wallet.Label,
        publicKey: PublicKeyEd25519,
        versions: List<WalletVersion>,
        type: Wallet.Type,
    ): List<WalletEntity> {
        val list = mutableListOf<WalletEntity>()
        for (version in versions) {
            val entity = WalletEntity(
                id = newWalletId(),
                publicKey = publicKey,
                type = type,
                version = version,
                label = label.copy(
                    accountName = createLabelName(
                        label.accountName, version.title, versions.size,
                    )
                )
            )
            list.add(entity)
        }

        insertWallets(list)
        return list.toList()
    }

    suspend fun addWatchWallet(
        label: Wallet.Label,
        publicKey: PublicKeyEd25519,
        version: WalletVersion,
    ): WalletEntity {
        return addWallet(label, publicKey, Wallet.Type.Watch, version)
    }

    suspend fun addNewWallet(label: Wallet.Label, mnemonic: List<String>): WalletEntity {
        val publicKey = vaultSource.addMnemonic(mnemonic)
        return addWallet(label, publicKey, Wallet.Type.Default, WalletVersion.V4R2)
    }

    private suspend fun addWallet(
        label: Wallet.Label,
        publicKey: PublicKeyEd25519,
        type: Wallet.Type,
        version: WalletVersion,
    ): WalletEntity {
        val entity = WalletEntity(
            id = newWalletId(),
            publicKey = publicKey,
            type = type,
            version = version,
            label = label
        )

        insertWallets(listOf(entity))
        return entity
    }

    private suspend fun insertWallets(list: List<WalletEntity>) {
        database.insertAccounts(list)
        for (wallet in list) {
            addWalletToRN(wallet)
        }
    }

    private fun createTonProofToken(wallet: WalletEntity): String? {
        val secretKey = vaultSource.getPrivateKey(wallet.publicKey) ?: return null
        val contract = wallet.contract
        val address = contract.address
        val payload = api.tonconnectPayload() ?: return null
        val proof = WalletProof.signTonkeeper(
            address = address,
            secretKey = secretKey,
            payload = payload,
            stateInit = contract.getStateCell().base64()
        )
        return try {
            api.tonconnectProof(address.toAccountId(), Json.encodeToString(proof))
        } catch (e: Throwable) {
            null
        }
    }

    fun safeSetSelectedWallet(id: String?) {
        scope.launch { setSelectedWallet(id) }
    }

    suspend fun setSelectedWallet(id: String?) {
        storageSource.setSelectedId(id)
        if (id == null) {
            _selectedStateFlow.value = SelectedState.Empty
            return
        }

        val entity = database.getAccount(id)
        if (entity == null) {
            setSelectedWallet(null)
        } else {
            _selectedStateFlow.value = SelectedState.Wallet(entity)
            rnLegacy.setSelectedWallet(id)
        }
    }

    fun deleteSelected() {
        scope.launch {
            selectedId?.let { delete(it) }
        }
    }

    suspend fun delete(id: String) = withContext(scope.coroutineContext) {
        database.deleteAccount(id)
        setSelectedWallet(database.getFirstAccountId())
    }

    suspend fun logout() = withContext(scope.coroutineContext) {
        database.clearAccounts()
        setSelectedWallet(null)
    }

    suspend fun getWalletByAccountId(accountId: String, testnet: Boolean = false): WalletEntity? {
        val wallets = database.getAccounts()
        val wallet = wallets.firstOrNull {
            it.accountId.equals(accountId, ignoreCase = true)
        } ?: return null
        if (wallet.testnet == testnet) {
            return wallet
        }
        return null
    }

    suspend fun getWalletById(id: String): WalletEntity? {
        return database.getAccount(id)
    }

    suspend fun getSelectedWallet(): WalletEntity? {
        return (_selectedStateFlow.value as? SelectedState.Wallet)?.wallet ?: database.getAccount(selectedId ?: return null)
    }

    suspend fun getSeqno(
        wallet: WalletEntity
    ): Int = withContext(Dispatchers.IO) {
        try {
            api.getAccountSeqno(wallet.accountId, wallet.testnet)
        } catch (e: Throwable) {
            0
        }
    }

    suspend fun getValidUntil(testnet: Boolean): Long {
        val seconds = api.getServerTime(testnet)
        return seconds + (5 * 30L) // 5 minutes
    }

    fun messageBody(
        wallet: WalletEntity,
        seqno: Int,
        validUntil: Long,
        transfers: List<WalletTransfer>
    ): MessageBodyEntity {
        val body = wallet.createBody(seqno, validUntil, transfers)
        return MessageBodyEntity(seqno, body, validUntil)
    }

    fun createSignedMessage(
        wallet: WalletEntity,
        seqno: Int,
        privateKeyEd25519: PrivateKeyEd25519,
        validUntil: Long,
        transfers: List<WalletTransfer>
    ): Cell {
        val data = messageBody(wallet, seqno, validUntil, transfers)
        return wallet.sign(privateKeyEd25519, data.seqno, data.body)
    }
}