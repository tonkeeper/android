package com.tonapps.wallet.data.multichain.vault

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface VaultMetadataDao {

    @Query("SELECT value FROM vault_metadata WHERE `key` = :key")
    fun get(key: String): ByteArray?

    @Query("SELECT * FROM vault_metadata")
    fun getAll(): List<VaultMetadataEntity>

    @Query("SELECT * FROM vault_metadata WHERE `key` IN (:keys)")
    fun getAll(keys: List<String>): List<VaultMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(entity: VaultMetadataEntity)

    @Transaction
    fun putAll(entities: List<VaultMetadataEntity>) {
        entities.forEach { put(it) }
    }

    @Query("DELETE FROM vault_metadata WHERE `key` = :key")
    fun delete(key: String)

    @Query("DELETE FROM vault_metadata WHERE `key` IN (:keys)")
    fun deleteAll(keys: List<String>)

    @Query("DELETE FROM vault_metadata")
    fun clear()
}
