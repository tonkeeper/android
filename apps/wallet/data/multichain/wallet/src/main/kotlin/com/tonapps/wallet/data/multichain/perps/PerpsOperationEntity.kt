package com.tonapps.wallet.data.multichain.perps

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "perps_operation",
    indices = [Index("wallet_id")],
)
data class PerpsOperationEntity(
    @PrimaryKey
    @ColumnInfo("operation_id")
    val operationId: String,

    @ColumnInfo("wallet_id")
    val walletId: String,

    @ColumnInfo("market_index")
    val marketIndex: Int,

    @ColumnInfo("kind", defaultValue = "'OPEN'")
    val kind: String,

    @ColumnInfo("side")
    val side: String,

    @ColumnInfo("margin_quote")
    val marginQuote: String,

    @ColumnInfo("leverage")
    val leverage: Int,

    @ColumnInfo("review_json")
    val reviewJson: String?,

    @ColumnInfo("plan_digest")
    val planDigest: String?,

    @ColumnInfo("expected_base_size")
    val expectedBaseSize: String?,

    @ColumnInfo("base_before")
    val baseBefore: String?,

    @ColumnInfo("margin_before")
    val marginBefore: String?,

    @ColumnInfo("status")
    val status: String,

    @ColumnInfo("error")
    val error: String?,

    @ColumnInfo("created_at")
    val createdAt: Long,

    @ColumnInfo("updated_at")
    val updatedAt: Long,
)
