package com.tonkeeper.api.jetton.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tonkeeper.api.fromJSON
import io.tonapi.models.JettonBalance

@Dao
interface JettonDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJettons(jettons: List<JettonEntity>)

    suspend fun insertJettons(accountId: String, jettons: List<JettonBalance>) {
        insertJettons(JettonEntity.map(accountId, jettons))
    }

    @Query("SELECT * FROM jetton WHERE accountId = :accountId")
    suspend fun getAllJettons(accountId: String): List<JettonEntity>

    suspend fun getJettons(accountId: String): List<JettonBalance> {
        return getAllJettons(accountId).map {
            fromJSON(it.data)
        }
    }
}
