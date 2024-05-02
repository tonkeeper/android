package com.tonapps.wallet.data.account.legacy.storage

import android.content.Context
import android.util.Log
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.wallet.data.account.WalletSource
import com.tonapps.wallet.data.account.WalletType
import com.tonapps.wallet.data.account.legacy.DebugWallet
import com.tonapps.wallet.data.account.legacy.IsDebug
import com.tonapps.wallet.data.account.legacy.WalletLegacy
import core.keyvalue.KeyValue
import org.ton.api.pub.PublicKeyEd25519
import org.ton.crypto.hex

internal class WalletStorage(context: Context) {

    private companion object {
        private const val CURRENT_WALLET_KEY = "current_wallet"
    }

    private val mnemonicStorage = MnemonicStorage(context)
    private val keyValue = KeyValue(context, "tonkeeper_def")
    private val wallets = Wallets(keyValue)

    suspend fun getSeed(id: Long): ByteArray? {
        return mnemonicStorage.getSeed(id)
    }

    suspend fun setSeed(id: Long, seed: ByteArray) {
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
        wallets.getIds().firstOrNull()?.let {
            setSelectedWallet(it)
        }
    }

    suspend fun getSelectedWallet(): Long {
        return keyValue.getLong(CURRENT_WALLET_KEY, 0)
    }

    suspend fun setSelectedWallet(walletId: Long) {
        keyValue.putLong(CURRENT_WALLET_KEY, walletId)
    }

    suspend fun getWallet(
        id: Long = 0,
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

    suspend fun getMnemonic(id: Long): List<String> {
        return mnemonicStorage.get(id)
    }
}