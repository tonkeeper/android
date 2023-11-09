package com.tonkeeper.core.tonconnect.db

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import core.extensions.toByteArrayFromBase64

@Entity(tableName = "ton_connect_app")
data class AppEntity(
    @PrimaryKey val id: String,
    val url: String,
    val accountId: String,
    val clientId: String,
    val publicKeyBase64: String
) {

    @Ignore val publicKey: ByteArray = publicKeyBase64.toByteArrayFromBase64()
}
