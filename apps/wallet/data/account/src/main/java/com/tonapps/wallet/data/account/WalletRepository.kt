package com.tonapps.wallet.data.account

import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.entities.WalletEvent
import com.tonapps.wallet.data.account.entities.WalletLabel
import com.tonapps.wallet.data.account.legacy.WalletManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.mnemonic.Mnemonic

class WalletRepository(
    private val scope: CoroutineScope,
    private val legacyManager: WalletManager,
    private val api: API
) {

    private val _walletsFlow = MutableStateFlow<List<WalletEntity>?>(null)
    val walletsFlow = _walletsFlow.asStateFlow().filterNotNull()

    private val _activeWalletFlow = MutableStateFlow<WalletEntity?>(null)
    val activeWalletFlow = _activeWalletFlow.asStateFlow().filterNotNull()

    @OptIn(ExperimentalCoroutinesApi::class)
    val realtimeEventsFlow = activeWalletFlow.flatMapLatest { wallet ->
        api.subscribe(wallet.accountId, wallet.testnet).map { event ->
            val isBoc = event.json.has("boc")
            if (isBoc) {
                WalletEvent.Boc(wallet, event.json)
            } else {
                WalletEvent.Transaction(wallet, event.json)
            }
        }
    }.catch { }.flowOn(Dispatchers.IO).shareIn(scope, SharingStarted.Eagerly, 1)

    init {
        scope.launch(Dispatchers.IO) {
            updateWallets()
        }
    }

    suspend fun setActiveWallet(id: Long) = withContext(Dispatchers.IO) {
        val activeWalletId = legacyManager.getActiveWallet()
        if (activeWalletId == id) {
            return@withContext
        }
        legacyManager.setActiveWallet(id)?.let {
            _activeWalletFlow.value = WalletEntity(it)
        }
    }

    suspend fun editLabel(
        id: Long,
        name: String,
        emoji: String,
        color: Int
    ) {
        val legacyWallet = legacyManager.edit(id, name, emoji, color) ?: return
        val walletEntity = WalletEntity(legacyWallet)
        _activeWalletFlow.value = walletEntity
        _walletsFlow.value = _walletsFlow.value?.map {
            if (it.id == id) {
                walletEntity
            } else {
                it
            }
        }
    }

    fun saveLabel(
        name: String,
        emoji: String,
        color: Int
    ) {
        scope.launch(Dispatchers.IO) {
            editLabel(name, emoji, color)
        }
    }

    suspend fun editLabel(
        name: String,
        emoji: String,
        color: Int
    ) {
        val id = legacyManager.getActiveWallet()
        editLabel(id, name, emoji, color)
    }

    private suspend fun updateWallets() {
        val activeWalletId = legacyManager.getActiveWallet()
        val wallets = getWallets()
        _walletsFlow.value = wallets
        _activeWalletFlow.value = wallets.find { it.id == activeWalletId }
    }

    suspend fun createNewWallet(label: WalletLabel) {
        val mnemonic = Mnemonic.generate()
        val legacy = legacyManager.addWallet(
            mnemonic = mnemonic,
            name = label.name,
            emoji = label.emoji,
            color = label.color,
            testnet = false,
            source = WalletSource.Default,
            version = WalletVersion.V4R2
        )

        updateWallets()
    }

    suspend fun addWatchWallet(
        publicKey: PublicKeyEd25519,
        label: WalletLabel,
        version: WalletVersion,
        source: WalletSource
    ) {
        val legacy = legacyManager.addWatchWallet(
            publicKey = publicKey,
            name = label.name,
            emoji = label.emoji,
            color = label.color,
            singer = false,
            version = version,
            source = source
        )

        updateWallets()
    }

    suspend fun addWallets(
        mnemonic: List<String>,
        publicKey: PublicKeyEd25519,
        versions: List<WalletVersion>,
        name: String? = null,
        emoji: CharSequence,
        color: Int,
        testnet: Boolean
    ) {
        for (version in versions) {
            val nameWithVersion = if (versions.size > 1) {
                "$name ${version.title}"
            } else {
                name
            }
            legacyManager.addWallet(
                mnemonic = mnemonic,
                publicKey = publicKey,
                version = version,
                name = nameWithVersion ?: "Wallet",
                emoji = emoji,
                color = color,
                testnet = testnet,
                source = WalletSource.Default
            )
        }

        updateWallets()
    }

    suspend fun addSignerWallet(
        publicKey: PublicKeyEd25519,
        name: String,
        emoji: CharSequence,
        color: Int,
        source: WalletSource
    ) {
        legacyManager.addWatchWallet(
            publicKey = publicKey,
            name = name,
            emoji = emoji,
            color = color,
            singer = true,
            version = WalletVersion.V4R2,
            source = source
        )

        updateWallets()
    }

    suspend fun getMnemonic(id: Long): Array<String> = withContext(Dispatchers.IO) {
        val mnemonic = legacyManager.getMnemonic(id)
        mnemonic.toTypedArray()
    }

    suspend fun getPrivateKey(id: Long): PrivateKeyEd25519 = withContext(Dispatchers.IO) {
        legacyManager.getPrivateKey(id)
    }

    private suspend fun getWallets(): List<WalletEntity> {
        val list = mutableListOf<WalletEntity>()
        val legacyWallets = legacyManager.getWallets()
        for (legacyWallet in legacyWallets) {
            list.add(WalletEntity(legacyWallet))
        }
        return list
    }
 }