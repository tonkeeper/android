package com.tonapps.tonkeeper.api.account.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tonapps.tonkeeper.api.fromJSON
import com.tonapps.tonkeeper.api.toJSON
import io.tonapi.models.Account

@Dao
interface AccountDao {

    @Query("DELETE FROM account WHERE accountId = :accountId")
    suspend fun delete(accountId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity)

    suspend fun insert(
        accountId: String,
        data: Account
    ) {
        insert(AccountEntity(accountId, toJSON(data)))
    }

    @Query("SELECT data FROM account WHERE accountId = :accountId LIMIT 1")
    suspend fun getByAccountId(accountId: String): String?

    suspend fun get(accountId: String): Account? {
        val data = getByAccountId(accountId) ?: return null
        return fromJSON(data)
    }
}