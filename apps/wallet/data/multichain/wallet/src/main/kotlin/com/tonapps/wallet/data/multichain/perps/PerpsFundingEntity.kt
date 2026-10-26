package com.tonapps.wallet.data.multichain.perps

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "perps_funding",
    indices = [Index("wallet_id")],
)
data class PerpsFundingEntity(
    @PrimaryKey
    @ColumnInfo("operation_id")
    val operationId: String,

    @ColumnInfo("wallet_id")
    val walletId: String,

    @ColumnInfo("kind")
    val kind: String,

    @ColumnInfo("stage")
    val stage: String,

    @ColumnInfo("route")
    val route: String,

    @ColumnInfo("beneficiary")
    val beneficiary: String,

    @ColumnInfo("source_asset_id")
    val sourceAssetId: String,

    @ColumnInfo("source_symbol")
    val sourceSymbol: String,

    @ColumnInfo("source_decimals")
    val sourceDecimals: Int,

    @ColumnInfo("source_amount")
    val sourceAmount: String,

    @ColumnInfo("target_amount")
    val targetAmount: String,

    @ColumnInfo("quote_id")
    val quoteId: String?,

    @ColumnInfo("execution_id")
    val executionId: String?,

    @ColumnInfo("source_tx_hash")
    val sourceTxHash: String?,

    @ColumnInfo("l1_tx_hash")
    val l1TxHash: String?,

    @ColumnInfo("steps_json")
    val stepsJson: String,

    @ColumnInfo("link_signature")
    val linkSignature: String?,

    @ColumnInfo("link_deadline")
    val linkDeadline: Long?,

    @ColumnInfo("registration_json")
    val registrationJson: String?,

    @ColumnInfo("deposit_id")
    val depositId: String?,

    @ColumnInfo("credited_amount")
    val creditedAmount: String?,

    @ColumnInfo("account_index")
    val accountIndex: Long?,

    @ColumnInfo("failure_reason")
    val failureReason: String?,

    @ColumnInfo("revision")
    val revision: Int,

    @ColumnInfo("created_at")
    val createdAt: Long,

    @ColumnInfo("updated_at")
    val updatedAt: Long,
)
