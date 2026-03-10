package com.tonapps.wallet.data.events.tx.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tonapps.wallet.api.entity.value.Blockchain
import com.tonapps.wallet.api.entity.value.BlockchainAddress
import com.tonapps.wallet.api.entity.value.Timestamp
import com.tonapps.wallet.data.events.tx.model.TxEvent

@Dao
interface TxRecordDao {

    @Query("""
        SELECT event FROM tx_records 
        WHERE account = :account 
        AND blockchain IN (:blockchains)
        AND (:beforeTimestamp IS NULL OR timestamp < :beforeTimestamp)
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    fun getEvents(
        account: BlockchainAddress,
        blockchains: List<Blockchain>,
        beforeTimestamp: Timestamp?,
        limit: Int
    ): List<TxEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    fun insertRecords(records: List<TxRecordEntity>)
}