package com.tonapps.wallet.data.dapps.source.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ConnectDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(row: ConnectEntity)

    @Query("DELETE FROM connect WHERE id = :id")
    fun deleteById(id: String): Int

    @Query("DELETE FROM connect WHERE id IN (:ids)")
    fun deleteByIds(ids: List<String>): Int

    @Query("UPDATE connect SET key_pair = :keyPair WHERE id = :id")
    fun updateKeyPair(id: String, keyPair: ByteArray): Int

    // TonConnect

    @Query("SELECT * FROM connect WHERE provider = 'ton_connect'")
    fun getTonConnectAll(): List<ConnectEntity>

    @Query("SELECT * FROM connect WHERE provider = 'ton_connect' AND account_id = :accountId AND mode = :mode")
    fun getTonConnectByWallet(accountId: String, mode: Int): List<ConnectEntity>

    @Query("SELECT * FROM connect WHERE provider = 'ton_connect' AND key_pair IS NULL")
    fun getTonConnectMissingKeyPair(): List<ConnectEntity>

    @Query("SELECT * FROM connect WHERE provider = 'ton_connect' AND id = :id LIMIT 1")
    fun getTonConnectById(id: String): ConnectEntity?

    @Query("DELETE FROM connect WHERE provider = 'ton_connect'")
    fun clearTonConnect(): Int

    // WalletConnect

    @Query("SELECT * FROM connect WHERE provider = 'wallet_connect' AND wallet_id = :walletId")
    fun getWalletConnectByWalletId(walletId: String): List<ConnectEntity>

    @Query("SELECT * FROM connect WHERE provider = 'wallet_connect' AND topic = :topic LIMIT 1")
    fun getWalletConnectByTopic(topic: String): ConnectEntity?

    @Query("DELETE FROM connect WHERE provider = 'wallet_connect' AND topic = :topic")
    fun deleteWalletConnectByTopic(topic: String): Int

    @Query("UPDATE connect SET topic = :topic WHERE provider = 'wallet_connect' AND id = :pairingTopic")
    fun updateWalletConnectTopic(pairingTopic: String, topic: String): Int
}
