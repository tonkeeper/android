package ton.wallet

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.mnemonic.Mnemonic
import ton.wallet.storage.WalletStorage

class WalletManager(
    application: Application
) {

    companion object {
        const val MNEMONIC_WORD_COUNT = Mnemonic.DEFAULT_WORD_COUNT
    }

    private val storage = WalletStorage(application)

    private var cacheWallet: Wallet? = null
    private var cacheWallets: MutableList<Wallet> = mutableListOf()

    fun hasWallet(): Boolean {
        return storage.hasWallet()
    }

    suspend fun getMnemonic(id: Long): List<String> {
        return storage.getMnemonic(id)
    }

    suspend fun getPrivateKey(id: Long): PrivateKeyEd25519 {
        val mnemonic = getMnemonic(id)
        val seed = Mnemonic.toSeed(mnemonic)
        return PrivateKeyEd25519(seed)
    }

    suspend fun setWalletName(address: String, name: String) {
        val createDate = getIdAddress(address)
        storage.setWalletName(createDate, name)
        cacheWallets.clear()
    }

    private suspend fun getIdAddress(
        address: String
    ): Long {
        val wallets = getWallets()
        val wallet = wallets.find { it.address == address || it.accountId == address }
        return wallet?.id ?: 0
    }

    suspend fun logout() {
        logout(getActiveWallet())
    }

    suspend fun logout(address: String? = null) {
        if (address == null) {
            logout()
            return
        }

        val createDate = getIdAddress(address)
        logout(createDate)
    }

    suspend fun logout(
        createDate: Long
    ) = withContext(Dispatchers.IO) {
        val currentWallets = getWallets()
        if (1 >= currentWallets.size) {
            clearAll()
        } else {
            val wallet = currentWallets.find { it.id == createDate } ?: return@withContext

            storage.deleteWallet(wallet.id)
            cacheWallets.remove(wallet)

            if (wallet == cacheWallet) {
                cacheWallet = null
                getWalletInfo()
            }
        }
    }

    private suspend fun clearAll() = withContext(Dispatchers.IO) {
        storage.clearAll()
        cacheWallets.clear()
        cacheWallet = null
    }

    suspend fun getWalletInfo(): Wallet? = withContext(Dispatchers.IO) {
        if (cacheWallet != null) {
            return@withContext cacheWallet
        }

        val activeWallet = getActiveWallet()
        cacheWallet = cacheWallets.find { it.id == activeWallet }
        if (cacheWallet == null) {
            cacheWallet = storage.getWallet(activeWallet)
        }
        cacheWallet
    }

    suspend fun getWallets(): List<Wallet> = withContext(Dispatchers.IO) {
        if (cacheWallets.isEmpty()) {
            cacheWallets = storage.getWallets().toMutableList()
        }
        cacheWallets.toList()
    }

    suspend fun getActiveWallet(): Long {
        return storage.getSelectedWallet()
    }

    suspend fun setActiveWallet(walletId: Long) {
        storage.setSelectedWallet(walletId)
        cacheWallet = null
    }

    suspend fun addWallet(mnemonic: List<String>, name: String? = null) = withContext(Dispatchers.IO) {
        val seed = Mnemonic.toSeed(mnemonic)
        val privateKey = PrivateKeyEd25519(seed)
        val publicKey = privateKey.publicKey()

        val wallet = Wallet(
            id = System.currentTimeMillis(),
            name = null,
            publicKey = publicKey
        )

        storage.addWallet(wallet, mnemonic)
        name?.let {
            storage.setWalletName(wallet.id, it)
        }

        cacheWallet = null
        cacheWallets.clear()
    }

}
