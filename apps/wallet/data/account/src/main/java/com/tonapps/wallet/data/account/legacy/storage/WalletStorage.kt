package com.tonapps.wallet.data.account.legacy.storage

import android.content.Context
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.extensions.clear
import com.tonapps.extensions.prefs
import com.tonapps.extensions.putLong
import com.tonapps.extensions.putString
import com.tonapps.wallet.data.account.legacy.DebugWallet
import com.tonapps.wallet.data.account.legacy.IsDebug
import com.tonapps.wallet.data.account.legacy.WalletLegacy

internal class WalletStorage(context: Context) {

    private companion object {
        private const val CURRENT_WALLET_KEY = "current_wallet"
    }

    private val mnemonicStorage = MnemonicStorage(context)
    private val keyValue = context.prefs("tonkeeper_def")
    private val wallets = Wallets(keyValue)

    suspend fun getSeed(id: String): ByteArray? {
        return mnemonicStorage.getSeed(id)
    }

    suspend fun setSeed(id: String, seed: ByteArray) {
        mnemonicStorage.setSeed(id, seed)
    }

    suspend fun addWallet(
        wallet: WalletLegacy,
        mnemonic: List<String>
    ) {
        mnemonicStorage.add(wallet.id, mnemonic)
        wallets.add(wallet)
        setSelectedWallet(wallet.id)
    }

    fun hasWallet(): Boolean {
        return wallets.hasWallet()
    }

    suspend fun setWalletName(id: String, name: String?) {
        wallets.setName(id, name)
    }

    suspend fun setWalletEmoji(id: String, emoji: CharSequence) {
        wallets.setEmoji(id, emoji)
    }

    suspend fun setWalletColor(id: String, color: Int) {
        wallets.setColor(id, color)
    }

    suspend fun setWalletVersion(id: String, version: WalletVersion) {
        wallets.setVersion(id, version)
    }

    suspend fun clearAll() {
        mnemonicStorage.clearAll()
        keyValue.clear()
    }

    suspend fun deleteWallet(id: String) {
        mnemonicStorage.delete(id)
        wallets.delete(id)
        wallets.getIds().firstOrNull()?.let {
            setSelectedWallet(it)
        }
    }

    suspend fun getSelectedWallet(): Long {
        return keyValue.getLong(CURRENT_WALLET_KEY, 0)
    }

    suspend fun setSelectedWallet(id: String) {
        keyValue.putString(CURRENT_WALLET_KEY, id)
    }

    suspend fun getWallet(
        id: String = "0",
    ): WalletLegacy? {
        return wallets.get(id)
    }

    suspend fun getWallets(): List<WalletLegacy> {
        if (IsDebug) {
            val legacyWallet = DebugWallet
            val testList = mutableListOf<WalletLegacy>()
            testList.add(legacyWallet)
            return testList
        }

        val walletIds = wallets.getIds()
        val wallets = mutableMapOf<String, WalletLegacy>()
        for (walletId in walletIds) {
            val wallet = getWallet(walletId) ?: continue
            wallets[wallet.key] = wallet
        }
        return wallets.values.toList()
    }

    suspend fun getMnemonic(id: String): List<String> {
        return mnemonicStorage.get(id)
    }
}