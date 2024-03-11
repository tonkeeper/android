package com.tonapps.wallet.data.account

import android.graphics.Color
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.entities.WalletLabel
import com.tonapps.wallet.data.account.legacy.WalletManager
import com.tonapps.wallet.data.core.WalletCurrency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.api.pub.PublicKeyEd25519
import org.ton.mnemonic.Mnemonic

class WalletRepository(
    private val scope: CoroutineScope,
    private val legacyManager: WalletManager,
) {

    private val _walletsFlow = MutableStateFlow<List<WalletEntity>?>(null)
    val walletsFlow = _walletsFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

    private val _activeWalletFlow = MutableStateFlow<WalletEntity?>(null)
    val activeWalletFlow = _activeWalletFlow.stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()

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
        val words = Mnemonic.generate()
        val legacy = legacyManager.addWallet(words, label.name, label.emoji, label.color, false)

        updateWallets()
    }

    suspend fun addWatchWallet(
        publicKey: PublicKeyEd25519,
        label: WalletLabel,
        version: WalletVersion,
    ) {
        val legacy = legacyManager.addWatchWallet(publicKey, label.name, label.emoji, label.color, false, version)

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
            legacyManager.addWallet(mnemonic, publicKey, version, nameWithVersion, emoji, color, testnet)
        }

        updateWallets()
    }

    suspend fun getMnemonic(id: Long): Array<String> {
        val mnemonic = legacyManager.getMnemonic(id)
        return mnemonic.toTypedArray()
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