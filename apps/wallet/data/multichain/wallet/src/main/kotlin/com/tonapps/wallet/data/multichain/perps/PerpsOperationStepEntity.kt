package com.tonapps.wallet.data.multichain.perps

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "perps_operation_step",
    primaryKeys = ["operation_id", "step_id"],
)
data class PerpsOperationStepEntity(
    @ColumnInfo("operation_id")
    val operationId: String,

    @ColumnInfo("step_id")
    val stepId: String,

    @ColumnInfo("kind")
    val kind: String,

    @ColumnInfo("requires_step_id")
    val requiresStepId: String?,

    @ColumnInfo("attempt_id")
    val attemptId: String,

    @ColumnInfo("api_key_index")
    val apiKeyIndex: Int,

    @ColumnInfo("nonce")
    val nonce: Long?,

    @ColumnInfo("account_index")
    val accountIndex: Long?,

    @ColumnInfo("tx_type")
    val txType: Int?,

    @ColumnInfo("tx_hash")
    val txHash: String?,

    @ColumnInfo("tx_info")
    val txInfo: String?,

    @ColumnInfo("client_order_index")
    val clientOrderIndex: Long,

    @ColumnInfo("plan_id")
    val planId: String?,

    @ColumnInfo("tx_expiry")
    val txExpiry: Long?,

    @ColumnInfo("order_refs")
    val orderRefs: String?,

    @ColumnInfo("submitted_at")
    val submittedAt: Long,

    @ColumnInfo("status")
    val status: String,

    @ColumnInfo("error")
    val error: String?,

    @ColumnInfo("updated_at")
    val updatedAt: Long,
)
