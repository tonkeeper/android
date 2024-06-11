package com.tonapps.wallet.data.account.legacy

import android.content.Context
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.Extras
import com.tonapps.wallet.data.account.WalletProof
import com.tonapps.wallet.data.account.WalletSource
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.entities.WalletLabel
import com.tonapps.wallet.data.account.repository.BaseWalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.mnemonic.Mnemonic

class LegacyWalletRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val legacyManager: WalletManager,
    private val api: API
): BaseWalletRepository(scope, api) {

    private val extras = Extras(context, api)

    init {
        scope.launch(Dispatchers.IO) {
            updateWallets()
        }
    }

    override fun removeCurrent() {
        scope.launch {
            val wallet = activeWalletFlow.firstOrNull() ?: return@launch
            legacyManager.clear(wallet.id)
            updateWallets()
        }
    }

    override suspend fun getTonProofToken(walletId: String): String? = withContext(Dispatchers.IO) {
        val value = extras.getTonProofToken(walletId)
        if (value != null) {
            return@withContext value
        }
        try {
            val wallet = getWalletById(walletId) ?: return@withContext null
            if (!wallet.hasPrivateKey) {
                return@withContext null
            }
            val newValue = createTonProofToken(wallet)
            extras.setTonProofToken(walletId, newValue)
            newValue
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun createTonProofToken(wallet: WalletEntity): String {
        val secretKey = getPrivateKey(wallet.id)
        val contract = wallet.contract
        val address = contract.address
        val payload = api.tonconnectPayload()
        val proof = WalletProof.signTonkeeper(
            address = address,
            secretKey = secretKey,
            payload = payload,
            stateInit = contract.getStateCell().base64()
        )
        return api.tonconnectProof(address.toAccountId(), Json.encodeToString(proof))
    }

    override suspend fun getWalletByAccountId(accountId: String): WalletEntity? {
        val legacyWallet = legacyManager.getWallets().find { it.accountId == accountId } ?: return null
        return WalletEntity(legacyWallet)
    }

    override suspend fun getWalletById(id: String): WalletEntity? {
        val legacyWallet = legacyManager.getWallets().find { it.id == id } ?: return null
        return WalletEntity(legacyWallet)
    }

    override suspend fun setActiveWallet(id: String): WalletEntity? = withContext(Dispatchers.IO) {
        val activeWalletId = legacyManager.getActiveWallet()
        if (activeWalletId == id) {
            return@withContext null
        }
        val wallet = legacyManager.setActiveWallet(id)?.let {
            WalletEntity(it)
        } ?: return@withContext null
        _activeWalletFlow.value = wallet
        wallet
    }

    override suspend fun editLabel(id: String, name: String, emoji: String, color: Int) {
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

    override suspend fun editLabel(name: String, emoji: String, color: Int) {
        val id = legacyManager.getActiveWallet()
        editLabel(id, name, emoji, color)
    }

    override suspend fun createNewWallet(label: WalletLabel): WalletEntity {
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
        return WalletEntity(legacy)
    }

    override suspend fun addWatchWallet(
        publicKey: PublicKeyEd25519,
        label: WalletLabel,
        version: WalletVersion,
        source: WalletSource
    ): WalletEntity {
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
        return WalletEntity(legacy)
    }

    override suspend fun addWallets(
        mnemonic: List<String>,
        publicKey: PublicKeyEd25519,
        versions: List<WalletVersion>,
        name: String?,
        emoji: CharSequence,
        color: Int,
        testnet: Boolean
    ): List<WalletEntity> {
        val list = mutableListOf<WalletEntity>()
        for (version in versions) {
            val nameWithVersion = if (versions.size > 1) {
                "$name ${version.title}"
            } else {
                name
            }
            val legacy = legacyManager.addWallet(
                mnemonic = mnemonic,
                publicKey = publicKey,
                version = version,
                name = nameWithVersion ?: "Wallet",
                emoji = emoji,
                color = color,
                testnet = testnet,
                source = WalletSource.Default
            )
            list.add(WalletEntity(legacy))
        }

        updateWallets()
        return list
    }

    override suspend fun addSignerWallet(
        publicKey: PublicKeyEd25519,
        name: String,
        emoji: CharSequence,
        color: Int,
        source: WalletSource,
        versions: List<WalletVersion>
    ): List<WalletEntity> {
        val list = mutableListOf<WalletEntity>()
        for (version in versions) {
            val nameWithVersion = if (versions.size > 1) {
                "$name ${version.title}"
            } else {
                name
            }

            val legacy = legacyManager.addWatchWallet(
                publicKey = publicKey,
                name = nameWithVersion,
                emoji = emoji,
                color = color,
                singer = true,
                version = WalletVersion.V4R2,
                source = source
            )
            list.add(WalletEntity(legacy))
        }

        updateWallets()
        return list
    }

    override suspend fun getMnemonic(id: String): Array<String> = withContext(Dispatchers.IO) {
        val mnemonic = legacyManager.getMnemonic(id)
        mnemonic.toTypedArray()
    }

    override suspend fun getPrivateKey(id: String): PrivateKeyEd25519 = withContext(Dispatchers.IO) {
        legacyManager.getPrivateKey(id)
    }

    override suspend fun clear() {
        legacyManager.clearAll()
        _walletsFlow.value = null
        _activeWalletFlow.value = null

        updateWallets()
    }

    private suspend fun updateWallets() {
        val activeWalletId = getActiveWalletId()
        val wallets = getWallets()
        _walletsFlow.value = wallets
        _activeWalletFlow.value = wallets.find { it.id == activeWalletId }
    }

    override suspend fun getWallets(): List<WalletEntity> {
        val list = mutableListOf<WalletEntity>()
        val legacyWallets = legacyManager.getWallets()
        for (legacyWallet in legacyWallets) {
            list.add(WalletEntity(legacyWallet))
        }
        return list
    }

    override suspend fun getActiveWalletId() = legacyManager.getActiveWallet()
}