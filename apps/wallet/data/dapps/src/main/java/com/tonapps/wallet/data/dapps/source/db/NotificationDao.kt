package com.tonapps.wallet.data.dapps.source.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface NotificationDao {

    @Query("SELECT * FROM notifications WHERE account_id = :accountId")
    fun getByAccountId(accountId: String): List<NotificationRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(row: NotificationRow): Long

    @Query("DELETE FROM notifications WHERE account_id = :accountId")
    fun deleteByAccountId(accountId: String): Int

    @Transaction
    fun replaceForAccount(accountId: String, rows: List<NotificationRow>) {
        deleteByAccountId(accountId)
        for (row in rows) insert(row)
    }
}
