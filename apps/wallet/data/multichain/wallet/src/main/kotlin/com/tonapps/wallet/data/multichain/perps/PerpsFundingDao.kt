package com.tonapps.wallet.data.multichain.perps

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PerpsFundingDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(operation: PerpsFundingEntity): Long

    @Update
    suspend fun update(operation: PerpsFundingEntity): Int

    @Query("SELECT * FROM perps_funding WHERE operation_id = :operationId")
    suspend fun get(operationId: String): PerpsFundingEntity?

    @Query("SELECT * FROM perps_funding WHERE wallet_id = :walletId ORDER BY created_at ASC")
    suspend fun getOperations(walletId: String): List<PerpsFundingEntity>

    @Query("SELECT * FROM perps_funding WHERE wallet_id = :walletId ORDER BY created_at ASC")
    fun observeOperations(walletId: String): Flow<List<PerpsFundingEntity>>

    @Query("SELECT * FROM perps_funding ORDER BY created_at ASC")
    suspend fun getAll(): List<PerpsFundingEntity>

    @Query("DELETE FROM perps_funding WHERE operation_id IN (:operationIds)")
    suspend fun delete(operationIds: List<String>)
}
