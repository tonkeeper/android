package com.tonapps.wallet.data.account

import android.graphics.Color
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

    suspend fun createNewWallet() {
        val words = Mnemonic.generate()
        val legacy = legacyManager.addWallet(words, "Wallet", "\uD83D\uDC8E", WalletColor.all.first(), false)

        updateWallets()
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