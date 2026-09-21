package com.tonapps.wallet.data.multichain.wallet

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WalletDao {

    @Query("SELECT * FROM wallet WHERE id = :id")
    fun getWallet(id: String): McWalletEntity?

    @Query("SELECT * FROM wallet")
    fun getWallets(): List<McWalletEntity>

    @Query("SELECT COUNT(*) FROM wallet")
    fun getWalletsCount(): Int

    @Query("SELECT * FROM wallet WHERE credential_id = :credentialId")
    fun getWalletByCredentialId(credentialId: String): McWalletEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWallet(wallet: McWalletEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWallets(wallets: List<McWalletEntity>)

    @Query("DELETE FROM wallet WHERE id = :id")
    fun deleteWallet(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM wallet WHERE id = :id)")
    fun walletExists(id: String): Boolean

    @Query("UPDATE wallet SET name = :name, emoji = :emoji, color = :color WHERE id = :id")
    fun updateLabel(id: String, name: String, emoji: String, color: Int)

    @Query("UPDATE wallet SET backup_type = :backupType, backup_time = :backupTime WHERE id = :id")
    fun updateBackup(id: String, backupType: McBackupSource?, backupTime: Long?)
}
