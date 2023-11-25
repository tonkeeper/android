package com.tonkeeper.api.jetton.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tonkeeper.api.fromJSON
import io.tonapi.models.JettonBalance

@Dao
interface JettonDao {

    @Query("DELETE FROM jetton WHERE accountId = :accountId")
    suspend fun delete(accountId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(jettons: List<JettonEntity>)

    suspend fun insert(accountId: String, jettons: List<JettonBalance>) {
        insert(JettonEntity.map(accountId, jettons))
    }

    @Query("SELECT * FROM jetton WHERE accountId = :accountId")
    suspend fun getByAccountId(accountId: String): List<JettonEntity>

    @Query("SELECT * FROM jetton WHERE accountId = :accountId AND jettonAddress = :address LIMIT 1")
    suspend fun getByAddress(accountId: String, address: String): JettonEntity?

    suspend fun get(accountId: String): List<JettonBalance> {
        return getByAccountId(accountId).map {
            fromJSON(it.data)
        }
    }
}
