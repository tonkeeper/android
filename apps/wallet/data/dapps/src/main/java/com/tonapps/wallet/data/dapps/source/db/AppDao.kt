package com.tonapps.wallet.data.dapps.source.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppDao {

    @Query("SELECT * FROM app WHERE url IN (:urls)")
    fun getByUrls(urls: List<String>): List<AppRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(row: AppRow)
}
