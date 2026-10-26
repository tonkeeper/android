package com.tonapps.wallet.data.multichain.wallet

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CredentialDao {

    @Query("SELECT * FROM credential WHERE id = :id")
    fun getCredential(id: String): CredentialEntity?

    @Query("SELECT * FROM credential WHERE id IN (:ids)")
    fun getCredentials(ids: List<String>): List<CredentialEntity>

    @Query("SELECT data FROM credential WHERE id = :id")
    fun getData(id: String): ByteArray?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCredential(credential: CredentialEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCredentials(credentials: List<CredentialEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertCredentialIfAbsent(credential: CredentialEntity): Long

    @Query("DELETE FROM credential WHERE id = :id")
    fun deleteCredential(id: String)

    /** Deletes the row only while it still holds [data]: a write that landed since is kept. */
    @Query("DELETE FROM credential WHERE id = :id AND data = :data")
    fun deleteCredentialIfUnchanged(id: String, data: ByteArray)

    @Query("SELECT EXISTS(SELECT 1 FROM credential WHERE id = :id)")
    fun credentialExists(id: String): Boolean
}
