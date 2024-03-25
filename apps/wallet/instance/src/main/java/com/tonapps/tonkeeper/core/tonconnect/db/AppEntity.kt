package com.tonapps.tonkeeper.core.tonconnect.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ton_connect_app")
data class AppEntity(
    @PrimaryKey val id: String,
    val url: String,
    val accountId: String,
    val clientId: String,
    val publicKeyHex: String
)