package com.tonapps.wallet.data.multichain.vault

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_metadata")
class VaultMetadataEntity(
    @PrimaryKey
    @ColumnInfo("key")
    val key: String,

    @ColumnInfo("value", typeAffinity = ColumnInfo.BLOB)
    val value: ByteArray,
)
