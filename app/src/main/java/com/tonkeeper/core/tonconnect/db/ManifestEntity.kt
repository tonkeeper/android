package com.tonkeeper.core.tonconnect.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ton_connect_manifest")
data class ManifestEntity(
    @PrimaryKey val url: String,
    val data: String
)