package com.tonapps.wallet.data.dapps.source.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app",
    indices = [Index(value = ["url"], unique = true)]
)
data class AppRow(
    @PrimaryKey
    @ColumnInfo("url")
    val url: String,

    @ColumnInfo("name")
    val name: String?,

    @ColumnInfo("icon_url")
    val iconUrl: String?,
)
