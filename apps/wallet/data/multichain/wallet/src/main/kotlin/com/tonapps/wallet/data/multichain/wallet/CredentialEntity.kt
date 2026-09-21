package com.tonapps.wallet.data.multichain.wallet

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tonapps.extensions.generateUuid

@Entity(
    tableName = "credential",
)
class CredentialEntity(
    @ColumnInfo("data")
    val data: ByteArray,

    @PrimaryKey
    @ColumnInfo("id")
    val id: String = generateUuid(),
)
