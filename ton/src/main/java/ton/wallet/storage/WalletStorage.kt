package ton.wallet.storage

import android.content.Context
import com.tonapps.blockchain.ton.contract.WalletVersion
import core.keyvalue.KeyValue
import ton.wallet.Wallet

internal class WalletStorage(context: Context) {

    private companion object {
        private const val CURRENT_WALLET_KEY = "current_wallet"
    }

    private val mnemonicStorage = MnemonicStorage(context)
    private val keyValue = KeyValue(context, "tonkeeper_def")
    private val wallets = Wallets(keyValue)

    suspend fun addWallet(
        wallet: Wallet,
        mnemonic: List<String>
    ) {
        mnemonicStorage.add(wallet.id, mnemonic)
        wallets.add(wallet)
        setSelectedWallet(wallet.id)
    }

    fun hasWallet(): Boolean {
        return wallets.hasWallet()
    }

    suspend fun setWalletName(createDate: Long, name: String?) {
        wallets.setName(createDate, name)
    }

    suspend fun setWalletEmoji(createDate: Long, emoji: CharSequence) {
        wallets.setEmoji(createDate, emoji)
    }

    suspend fun setWalletColor(createDate: Long, color: Int) {
        wallets.setColor(createDate, color)
    }

    suspend fun setWalletVersion(createDate: Long, version: WalletVersion) {
        wallets.setVersion(createDate, version)
    }

    suspend fun clearAll() {
        mnemonicStorage.clearAll()
        keyValue.clear()
    }

    suspend fun deleteWallet(createDate: Long) {
        mnemonicStorage.delete(createDate)
        wallets.delete(createDate)
        setSelectedWallet(wallets.getIds().first())
    }

    suspend fun getSelectedWallet(): Long {
        return keyValue.getLong(CURRENT_WALLET_KEY, 0)
    }

    suspend fun setSelectedWallet(walletId: Long) {
        keyValue.putLong(CURRENT_WALLET_KEY, walletId)
    }

    suspend fun getWallet(
        id: Long = 0,
    ): Wallet? {
        return wallets.get(id)
    }

    suspend fun getWallets(): List<Wallet> {
        val walletIds = wallets.getIds()
        val wallets = mutableMapOf<String, Wallet>()
        for (walletId in walletIds) {
            val wallet = getWallet(walletId) ?: continue
            wallets[wallet.key] = wallet
        }
        return wallets.values.toList()
    }

    suspend fun getMnemonic(id: Long): List<String> {
        return mnemonicStorage.get(id)
    }
}