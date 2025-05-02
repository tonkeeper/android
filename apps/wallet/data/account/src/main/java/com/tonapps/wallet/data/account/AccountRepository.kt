package com.tonapps.wallet.data.account

import android.app.KeyguardManager
import android.content.Context
import com.tonapps.blockchain.MnemonicHelper
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.contract.walletVersion
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.blockchain.tron.KeychainTrxAccountsProvider
import com.tonapps.ledger.ton.LedgerAccount
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.MessageBodyEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.source.DatabaseSource
import com.tonapps.wallet.data.account.source.StorageSource
import com.tonapps.wallet.data.account.source.VaultSource
import com.tonapps.wallet.data.core.recordException
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.rn.data.RNKeystone
import com.tonapps.wallet.data.rn.data.RNLedger
import com.tonapps.wallet.data.rn.data.RNWallet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.contract.wallet.WalletTransfer
import org.ton.mnemonic.Mnemonic
import java.math.BigInteger
import java.util.UUID

class AccountRepository(
    private val context: Context,
    private val api: API,
    private val rnLegacy: RNLegacy,
) {

    companion object {
        fun newWalletId(): String {
            return UUID.randomUUID().toString()
        }
    }

    sealed class SelectedState {
        data object Initialization : SelectedState()
        data object Empty : SelectedState()
        data class Wallet(val wallet: WalletEntity) : SelectedState()
    }

    private val keyguardManager by lazy {
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val database = DatabaseSource(context, scope)
    private val storageSource: StorageSource by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { StorageSource(context) }
    private val vaultSource: VaultSource by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { VaultSource(context) }
    private val migrationHelper = RNMigrationHelper(rnLegacy)

    private val _selectedStateFlow = MutableStateFlow<SelectedState>(SelectedState.Initialization)
    val selectedStateFlow = _selectedStateFlow.stateIn(
        scope,
        SharingStarted.Eagerly,
        SelectedState.Initialization
    )
    val selectedWalletFlow = selectedStateFlow.filterNotNull().filterIsInstance<SelectedState.Wallet>().map {
        it.wallet
    }.shareIn(scope, SharingStarted.Eagerly, 1).distinctUntilChanged()

    private val selectedId: String?
        get() = (selectedStateFlow.value as? SelectedState.Wallet)?.wallet?.id

    init {
        scope.launch(Dispatchers.IO) {
            if (rnLegacy.isRequestMainMigration()) {
                // database.clearAccounts()
                // storageSource.clear()
                migrationFromRN()
                rnLegacy.setWalletMigrated()
            }

            val selectedId = storageSource.getSelectedId()
            setSelectedWallet(selectedId)
        }
    }

    suspend fun setInitialized(id: String, initialized: Boolean) {
        try {
            database.setInitialized(id, initialized)
        } catch (e: Throwable) {
            recordException(e)
        }
    }

    suspend fun importPrivateKeysFromRNLegacy(passcode: String): Boolean = withContext(Dispatchers.IO) {
        val vaultState = migrationHelper.loadSecureStore(passcode)
        if (vaultState.hasError) {
            false
        } else {
            val list = vaultState.list()
            if (list.isNotEmpty()) {
                for (mnemonic in list) {
                    vaultSource.addMnemonic(mnemonic.mnemonic.split(" "))
                }
            }
            true
        }
    }

    fun addMnemonic(words: List<String>) {
        vaultSource.addMnemonic(words)
    }

    private suspend fun migrationFromRN() = withContext(Dispatchers.IO) {
        val (selectedId, wallets) = migrationHelper.loadLegacy()
        if (wallets.isNotEmpty()) {
            database.insertAccounts(wallets)
            for (wallet in wallets) {
                val token = rnLegacy.getTonProof(wallet.id) ?: continue
                storageSource.setTonProofToken(wallet.publicKey, token)
            }
            storageSource.setSelectedId(selectedId)
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
            Wallet.Type.Keystone -> RNWallet.Type.Keystone
            else -> RNWallet.Type.Regular
        }

        val rnWallet = RNWallet(
            name = wallet.label.accountName,
            color = RNWallet.resolveColor(wallet.label.color),
            emoji = RNWallet.fixEmoji(wallet.label.emoji),
            identifier = wallet.id,
            pubkey = wallet.publicKey.hex(),
            network = if (wallet.testnet) RNWallet.Network.Testnet else RNWallet.Network.Mainnet,
            type = type,
            version = walletVersion(wallet.version),
            workchain = wallet.contract.workchain,
            allowedDestinations = null,
            configPubKey = null,
            ledger = wallet.ledger?.let {
                RNLedger(
                    deviceId = it.deviceId,
                    accountIndex = it.accountIndex
                )
            },
            keystone = wallet.keystone?.let {
                RNKeystone(
                    xfp = it.xfp,
                    path = it.path
                )
            }
        )

        rnLegacy.addWallet(rnWallet)
    }

    fun editLabel(
        walletId: String,
        name: String,
        emoji: CharSequence,
        color: Int
    ) {
        scope.launch(scope.coroutineContext) {
            val selectedWallet = getSelectedWallet() ?: return@launch
            val isEditSelectedWallet = walletId.isBlank() || walletId == selectedWallet.id
            val wallet = if (isEditSelectedWallet) {
                selectedWallet
            } else {
                getWalletById(walletId) ?: return@launch
            }

            val newLabel = Wallet.Label(name, emoji, color)
            _selectedStateFlow.value = SelectedState.Wallet(wallet.copy(label = newLabel))

            database.editAccount(wallet.id, Wallet.Label(name, emoji, color))
            rnLegacy.edit(wallet.id, name, RNWallet.fixEmoji(emoji), color)
        }
    }

    suspend fun requestTonProofToken(wallet: WalletEntity): String? = withContext(scope.coroutineContext) {
        try {
            if (keyguardManager.isDeviceLocked) {
                return@withContext null
            }

            if (!wallet.hasPrivateKey) {
                return@withContext null
            }
            val token = storageSource.getTonProofToken(wallet.publicKey)
            if (token != null) {
                return@withContext token
            }
            val tonProofToken = createTonProofToken(wallet) ?: return@withContext null
            saveTonProof(wallet, tonProofToken)
            tonProofToken
        } catch (e: Throwable) {
            recordException(e)
            null
        }
    }


    private suspend fun saveTonProof(wallet: WalletEntity, token: String) = withContext(Dispatchers.IO) {
        storageSource.setTonProofToken(wallet.publicKey, token)

        val wallets = getWalletByPublicKey(wallet.publicKey, wallet.testnet)
        for (w in wallets) {
            rnLegacy.setTonProof(w.id, token)
        }
    }

    suspend fun getWallets() = database.getAccounts()

    suspend fun getUninitializedWallets() = database.getAccounts().filter { !it.initialized }

    suspend fun getInitializedWallets() = database.getAccounts().filter { it.initialized }

    fun getVaultKeys(): String {
        return vaultSource.getVaultKeys()
    }

    suspend fun getMnemonic(id: String): Array<String>? {
        val wallet = database.getAccount(id) ?: return null
        return vaultSource.getMnemonic(wallet.publicKey)
    }

    suspend fun getPrivateKey(id: String): PrivateKeyEd25519? {
        val wallet = database.getAccount(id) ?: return null
        return vaultSource.getPrivateKey(wallet.publicKey)
    }

    private suspend fun getTrxAccountsProvider(id: String): KeychainTrxAccountsProvider? {
        val mnemonic = getMnemonic(id)?.toList() ?: return null
        if (MnemonicHelper.isValidStandardTonMnemonic(mnemonic)) {
            val entropy = Mnemonic.toEntropy(mnemonic)
            return KeychainTrxAccountsProvider.fromEntropy(entropy)
        } else {
            return KeychainTrxAccountsProvider.fromMnemonic(mnemonic)
        }
    }

    suspend fun getTronMnemonic(id: String): Array<String>? {
        val trxAccountsProvider = getTrxAccountsProvider(id) ?: return null
        return trxAccountsProvider.mnemonics.toTypedArray()
    }

    suspend fun getTronAddress(id: String): String? {
        val trxAccountsProvider = getTrxAccountsProvider(id) ?: return null
        return trxAccountsProvider.getAddress()
    }

    suspend fun getTronPrivateKey(id: String): BigInteger? {
        val trxAccountsProvider = getTrxAccountsProvider(id) ?: return null
        return trxAccountsProvider.getPrivateKey()
    }

    suspend fun pairLedger(
        label: Wallet.NewLabel,
        ledgerAccounts: List<LedgerAccount>,
        deviceId: String,
        initialized: List<Boolean>
    ): List<WalletEntity> {
        val list = mutableListOf<WalletEntity>()
        for ((index, account) in ledgerAccounts.withIndex()) {
            val entity = WalletEntity(
                id = newWalletId(),
                publicKey = account.publicKey,
                type = Wallet.Type.Ledger,
                version = WalletVersion.V4R2,
                label = label.create(index),
                ledger = WalletEntity.Ledger(
                    deviceId = deviceId,
                    accountIndex = account.path.index
                ),
                initialized = initialized[index]
            )
            list.add(entity)
        }

        insertWallets(list)
        return list.toList()
    }

    suspend fun pairSigner(
        label: Wallet.NewLabel,
        publicKey: PublicKeyEd25519,
        versions: List<WalletVersion>,
        qr: Boolean,
        initialized: List<Boolean>
    ): List<WalletEntity> {
        val type = if (qr) Wallet.Type.SignerQR else Wallet.Type.Signer
        return addWallet(versions.map { newWalletId() }, label, publicKey, versions, type, initialized = initialized)
    }

    suspend fun pairKeystone(
        label: Wallet.NewLabel,
        publicKey: PublicKeyEd25519,
        keystone: WalletEntity.Keystone,
        initialized: Boolean
    ): List<WalletEntity> {
        val entity = WalletEntity(
            id = newWalletId(),
            publicKey = publicKey,
            type = Wallet.Type.Keystone,
            version = WalletVersion.V4R2,
            label = label.create(0),
            keystone = keystone,
            initialized = initialized
        )

        val list = listOf(entity)
        insertWallets(list)
        return list
    }

    suspend fun importWallet(
        ids: List<String>,
        label: Wallet.NewLabel,
        mnemonic: List<String>,
        versions: List<WalletVersion>,
        testnet: Boolean,
        initialized: List<Boolean>
    ): List<WalletEntity> {
        val publicKey = vaultSource.addMnemonic(mnemonic)
        val type = if (testnet) Wallet.Type.Testnet else Wallet.Type.Default
        return addWallet(ids, label, publicKey, versions, type, initialized = initialized)
    }

    suspend fun addWallet(
        ids: List<String>,
        label: Wallet.NewLabel,
        publicKey: PublicKeyEd25519,
        versions: List<WalletVersion>,
        type: Wallet.Type,
        initialized: List<Boolean>
    ): List<WalletEntity> {
        val list = mutableListOf<WalletEntity>()
        for ((index, version) in versions.withIndex()) {
            val entity = WalletEntity(
                id = ids.getOrNull(index) ?: newWalletId(),
                publicKey = publicKey,
                type = type,
                version = version,
                label = label.create(index),
                initialized = initialized[index]
            )
            list.add(entity)
        }

        insertWallets(list)
        return list.toList()
    }

    suspend fun addWatchWallet(
        label: Wallet.NewLabel,
        publicKey: PublicKeyEd25519,
        version: WalletVersion,
    ): WalletEntity {
        return addWallet(newWalletId(), label, publicKey, Wallet.Type.Watch, version, initialized = false)
    }

    suspend fun addNewWallet(
        id: String,
        label: Wallet.NewLabel,
        mnemonic: List<String>
    ): WalletEntity {
        val publicKey = vaultSource.addMnemonic(mnemonic)
        return addWallet(id, label, publicKey, Wallet.Type.Default, WalletVersion.V5R1, new = true, initialized = false)
    }

    private suspend fun addWallet(
        id: String,
        label: Wallet.NewLabel,
        publicKey: PublicKeyEd25519,
        type: Wallet.Type,
        version: WalletVersion,
        new: Boolean = false,
        initialized: Boolean
    ): WalletEntity {
        val entity = WalletEntity(
            id = id,
            publicKey = publicKey,
            type = type,
            version = version,
            label = label.create(0),
            initialized = initialized
        )

        insertWallets(listOf(entity), new)
        return entity
    }

    private suspend fun insertWallets(list: List<WalletEntity>, new: Boolean = false) {
        database.insertAccounts(list)
        for (wallet in list) {
            addWalletToRN(wallet)
            if (!new && wallet.hasPrivateKey) {
                requestTonProofToken(wallet)
            }
        }
    }

    private suspend fun createTonProofToken(wallet: WalletEntity): String? {
        val payload = api.tonconnectPayload() ?: return null
        return try {
            val publicKey = wallet.publicKey
            val contract = BaseWalletContract.create(publicKey, WalletVersion.V4R2.title, wallet.testnet)
            val secretKey = vaultSource.getPrivateKey(publicKey) ?: throw Exception("private key not found")
            val address = contract.address
            val proof = WalletProof.signTonkeeper(
                address = address,
                secretKey = secretKey,
                payload = payload,
                stateInit = contract.stateInitCell().base64()
            )
            api.tonconnectProof(address.toAccountId(), proof.string(false))
        } catch (e: Throwable) {
            recordException(e)
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

        val entity = database.getAccount(id) ?: database.getAccounts().firstOrNull()
        if (entity == null) {
            setSelectedWallet(null)
        } else {
            _selectedStateFlow.value = SelectedState.Wallet(entity)
            rnLegacy.setSelectedWallet(id)
        }
    }

    suspend fun delete(wallet: WalletEntity) = withContext(scope.coroutineContext) {
        database.deleteAccount(wallet.id)
        setSelectedWallet(database.getFirstAccountId())
    }

    suspend fun logout() = withContext(scope.coroutineContext) {
        database.clearAccounts()
        setSelectedWallet(null)
    }

    suspend fun getWalletsByAccountId(accountId: String, testnet: Boolean): List<WalletEntity> {
        return database.getAccounts().filter {
            it.accountId.equalsAddress(accountId) && it.testnet == testnet
        }
    }

    suspend fun getWalletByPublicKey(publicKey: PublicKeyEd25519, testnet: Boolean): List<WalletEntity> {
        return database.getAccounts().filter {
            it.publicKey == publicKey && it.testnet == testnet
        }
    }

    suspend fun getWalletByAccountId(accountId: String, testnet: Boolean = false): WalletEntity? {
        val wallets = getWalletsByAccountId(accountId, testnet)
        if (wallets.isEmpty()) {
            return null
        }
        if (wallets.size == 1) {
            return wallets.first()
        }
        return wallets.firstOrNull {
            it.hasPrivateKey
        } ?: wallets.firstOrNull {
            it.isTonConnectSupported
        } ?: wallets.first()
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
        api.getAccountSeqno(wallet.accountId, wallet.testnet)
    }

    suspend fun getValidUntil(
        testnet: Boolean
    ): Long = withContext(Dispatchers.IO) {
        val seconds = api.getServerTime(testnet)
        seconds + (5 * 30L) // 5 minutes
    }

    fun messageBody(
        wallet: WalletEntity,
        seqNo: Int,
        validUntil: Long,
        transfers: List<WalletTransfer>
    ): MessageBodyEntity {
        return MessageBodyEntity(wallet, seqNo, validUntil, transfers)
    }

    suspend fun messageBody(
        wallet: WalletEntity,
        validUntil: Long = 0,
        transfers: List<WalletTransfer>
    ): MessageBodyEntity {
        val seqNo = getSeqno(wallet)
        return messageBody(
            wallet = wallet,
            seqNo = seqNo,
            validUntil = if (validUntil > 0) validUntil else getValidUntil(wallet.testnet),
            transfers = transfers
        )
    }
}