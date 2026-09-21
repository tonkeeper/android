package com.tonapps.wallet.data.multichain.wallet

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CredentialDao {

    @Query("SELECT * FROM credential WHERE id = :id")
    fun getCredential(id: String): CredentialEntity?

    @Query("SELECT data FROM credential WHERE id = :id")
    fun getData(id: String): ByteArray?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCredential(credential: CredentialEntity)

    @Query("DELETE FROM credential WHERE id = :id")
    fun deleteCredential(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM credential WHERE id = :id)")
    fun credentialExists(id: String): Boolean
}
