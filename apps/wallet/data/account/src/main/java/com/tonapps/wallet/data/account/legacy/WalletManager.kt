package com.tonapps.wallet.data.account.legacy

import android.content.Context
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.wallet.data.account.WalletType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.mnemonic.Mnemonic
import com.tonapps.wallet.data.account.legacy.storage.WalletStorage

// TODO refactor WalletManager
class WalletManager(
    application: Context
) {

    companion object {
        const val MNEMONIC_WORD_COUNT = Mnemonic.DEFAULT_WORD_COUNT
    }

    private val storage = WalletStorage(application)

    private var cacheWallet: WalletLegacy? = null
    private var cacheWallets: MutableList<WalletLegacy> = mutableListOf()

    fun hasWallet(): Boolean {
        return storage.hasWallet()
    }

    suspend fun getMnemonic(id: String): List<String> {
        return storage.getMnemonic(id)
    }

    suspend fun getPrivateKey(id: String): PrivateKeyEd25519 {
        val seed = getSeed(id)
        if (seed.isEmpty()) {
            return EmptyPrivateKeyEd25519
        }
        return PrivateKeyEd25519(seed)
    }

    private suspend fun getSeed(id: String): ByteArray {
        val seed = storage.getSeed(id)
        if (seed != null) {
            return seed
        }
        return createSeed(id)
    }

    private suspend fun createSeed(id: String): ByteArray {
        val mnemonic = getMnemonic(id)
        if (mnemonic.isEmpty()) {
            return ByteArray(0)
        }
        val seed = Mnemonic.toSeed(mnemonic)
        storage.setSeed(id, seed)
        return seed
    }

    suspend fun edit(
        id: String,
        name: String,
        emoji: CharSequence,
        color: Int
    ): WalletLegacy? = withContext(Dispatchers.IO) {
        storage.setWalletName(id, name)
        storage.setWalletEmoji(id, emoji)
        storage.setWalletColor(id, color)
        cacheWallets = mutableListOf()
        cacheWallet = null
        getWalletInfo()
    }

    suspend fun setWalletVersion(id: String, version: WalletVersion) {
        storage.setWalletVersion(id, version)
        cacheWallets = mutableListOf()
        cacheWallet = null
    }

    private suspend fun getIdAddress(
        address: String
    ): String {
        val wallets = getWallets()
        val wallet = wallets.find { it.address == address || it.accountId == address }
        return wallet?.id ?: "0"
    }

    suspend fun logout() {
        logoutById(getActiveWallet())
    }

    suspend fun logoutByAddress(address: String? = null) {
        if (address == null) {
            logout()
            return
        }

        val id = getIdAddress(address)
        logoutById(id)
    }

    suspend fun logoutById(
        id: String
    ) = withContext(Dispatchers.IO) {
        val currentWallets = getWallets()
        if (1 >= currentWallets.size) {
            clearAll()
        } else {
            val wallet = currentWallets.find { it.id == id } ?: return@withContext

            storage.deleteWallet(wallet.id)
            cacheWallets.remove(wallet)

            if (wallet == cacheWallet) {
                cacheWallet = null
            }
        }
    }

    suspend fun clear(walletId: String) {
        storage.deleteWallet(walletId)
        cacheWallets.clear()
        cacheWallet = null
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        storage.clearAll()
        cacheWallets.clear()
        cacheWallet = null
    }

    suspend fun getWalletInfo(): WalletLegacy? = withContext(Dispatchers.IO) {
        if (IsDebug) {
            return@withContext DebugWallet
        }

        if (cacheWallet != null) {
            return@withContext cacheWallet
        }

        val wallets = getWallets()
        if (wallets.isEmpty()) {
            return@withContext null
        }

        val activeWallet = getActiveWallet()

        cacheWallet = wallets.find { it.id == activeWallet }
        if (cacheWallet == null) {
            cacheWallet = wallets.first()
        }

        cacheWallet
    }

    suspend fun getWallets(): List<WalletLegacy> = withContext(Dispatchers.IO) {
        if (cacheWallets.isEmpty()) {
            cacheWallets = storage.getWallets().toMutableList()
        }
        cacheWallets.toList()
    }

    suspend fun getActiveWallet(): String {
        if (IsDebug) {
            return DebugWallet.id.toString()
        }
        return storage.getSelectedWallet().toString()
    }

    suspend fun setActiveWallet(walletId: String): WalletLegacy? {
        storage.setSelectedWallet(walletId)
        cacheWallet = null

        return getWalletInfo()
    }

    suspend fun addWatchWallet(
        publicKey: PublicKeyEd25519,
        name: String,
        emoji: CharSequence,
        color: Int,
        type: WalletType,
        version: WalletVersion = WalletVersion.V4R2
    ): WalletLegacy = withContext(Dispatchers.IO) {
        val wallet = WalletLegacy(
            id = System.currentTimeMillis().toString(),
            name = name,
            publicKey = publicKey,
            type = type,
            emoji = emoji,
            color = color,
            version = version
        )

        insertWallet(wallet, emptyList())

        wallet
    }

    suspend fun addWallet(
        mnemonic: List<String>,
        name: String,
        emoji: CharSequence,
        color: Int,
        testnet: Boolean,
        version: WalletVersion,
    ): WalletLegacy = withContext(Dispatchers.IO) {
        val seed = Mnemonic.toSeed(mnemonic)
        val privateKey = PrivateKeyEd25519(seed)
        val publicKey = privateKey.publicKey()

        val wallet = WalletLegacy(
            id = System.currentTimeMillis().toString(),
            name = name,
            publicKey = publicKey,
            type = if (testnet) WalletType.Testnet else WalletType.Default,
            emoji = emoji,
            color = color,
            version = version
        )

        insertWallet(wallet, mnemonic)

        wallet
    }

    suspend fun addWallet(
        mnemonic: List<String>,
        publicKey: PublicKeyEd25519,
        version: WalletVersion,
        name: String,
        emoji: CharSequence,
        color: Int,
        testnet: Boolean,
    ): WalletLegacy = withContext(Dispatchers.IO) {
        val wallet = WalletLegacy(
            id = System.currentTimeMillis().toString(),
            name = name,
            publicKey = publicKey,
            type = if (testnet) WalletType.Testnet else WalletType.Default,
            emoji = emoji,
            color = color,
            version = version
        )

        insertWallet(wallet, mnemonic)

        wallet
    }

    private suspend fun insertWallet(wallet: WalletLegacy, mnemonic: List<String>) {
        storage.addWallet(wallet, mnemonic)

        cacheWallet = null
        cacheWallets.clear()
    }

}
