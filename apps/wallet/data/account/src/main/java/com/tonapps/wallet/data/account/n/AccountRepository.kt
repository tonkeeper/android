package com.tonapps.wallet.data.account.n

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.extensions.prefs
import com.tonapps.extensions.prefsEncrypted
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.WalletProof
import com.tonapps.wallet.data.account.WalletType
import com.tonapps.wallet.data.account.entities.MessageBodyEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.entities.WalletEvent
import com.tonapps.wallet.data.account.entities.WalletLabel
import com.tonapps.wallet.data.account.source.DatabaseSource
import com.tonapps.wallet.data.account.source.StorageSource
import com.tonapps.wallet.data.account.source.VaultSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val api: API
) {

    private companion object {
        private fun newWalletId(): String {
            return UUID.randomUUID().toString()
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

    private val _selectedStateFlow = MutableStateFlow<SelectedState>(SelectedState.Initialization)
    val selectedStateFlow = _selectedStateFlow.stateIn(scope, SharingStarted.Eagerly, SelectedState.Initialization)
    val selectedWalletFlow = selectedStateFlow.filterNotNull().filterIsInstance<SelectedState.Wallet>().map {
        it.wallet
    }

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
        scope.launch {
            setSelectedWallet(storageSource.getSelectedId())
        }
    }

    fun editLabel(name: String, emoji: CharSequence, color: Int) {
        scope.launch {
            val wallet = selectedWalletFlow.firstOrNull() ?: return@launch
            database.editAccount(wallet.id, WalletLabel(name, emoji, color))
        }
    }

    suspend fun editLabel(id: String, label: WalletLabel) = withContext(scope.coroutineContext) {
        database.editAccount(id, label)
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
        storageSource.setTonProofToken(id, tonProofToken)
        tonProofToken
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

    suspend fun pairSigner(
        label: WalletLabel,
        publicKey: PublicKeyEd25519,
        versions: List<WalletVersion>,
        qr: Boolean,
    ): List<WalletEntity> {
        val type = if (qr) WalletType.SignerQR else WalletType.SignerQR
        return addWallet(label, publicKey, versions, type)
    }

    suspend fun importWallet(
        label: WalletLabel,
        mnemonic: List<String>,
        versions: List<WalletVersion>,
        testnet: Boolean,
    ): List<WalletEntity> {
        val publicKey = vaultSource.addMnemonic(mnemonic)
        val type = if (testnet) WalletType.Testnet else WalletType.Default
        return addWallet(label, publicKey, versions, type)
    }

    private suspend fun addWallet(
        label: WalletLabel,
        publicKey: PublicKeyEd25519,
        versions: List<WalletVersion>,
        type: WalletType,
    ): List<WalletEntity> {
        val list = mutableListOf<WalletEntity>()
        for (version in versions) {
            val entity = WalletEntity(
                id = newWalletId(),
                publicKey = publicKey,
                type = type,
                version = version,
                label = if (versions.size == 1) {
                    label
                } else {
                    label.copy(
                        accountName = "${label.accountName} ${version.title}",
                    )
                }
            )
            list.add(entity)
        }

        insertWallets(list)
        return list.toList()
    }

    suspend fun addWatchWallet(
        label: WalletLabel,
        publicKey: PublicKeyEd25519,
        version: WalletVersion,
    ): WalletEntity {
        return addWallet(label, publicKey, WalletType.Watch, version)
    }

    suspend fun addNewWallet(label: WalletLabel): WalletEntity {
        val mnemonic = Mnemonic.generate()
        val publicKey = vaultSource.addMnemonic(mnemonic)
        return addWallet(label, publicKey, WalletType.Default, WalletVersion.V4R2)
    }

    private suspend fun addWallet(
        label: WalletLabel,
        publicKey: PublicKeyEd25519,
        type: WalletType,
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
        setSelectedWallet(list.first().id)
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

    suspend fun getWalletByAccountId(accountId: String): WalletEntity? {
        val wallets = database.getAccounts()
        return wallets.firstOrNull { it.accountId.equals(accountId, ignoreCase = true) }
    }

    suspend fun getWalletById(id: String): WalletEntity? {
        return database.getAccount(id)
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