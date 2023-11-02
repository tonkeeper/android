package com.tonkeeper.api.account.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tonkeeper.api.fromJSON
import io.tonapi.models.Account

@Dao
interface AccountDao {

    @Query("DELETE FROM account WHERE accountId = :accountId")
    suspend fun delete(accountId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity)

    @Query("SELECT * FROM account WHERE accountId = :accountId LIMIT 1")
    suspend fun getByAccountId(accountId: String): AccountEntity?

    suspend fun get(accountId: String): Account? {
        val entity = getByAccountId(accountId) ?: return null
        return fromJSON(entity.data)
    }
}