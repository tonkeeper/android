package com.tonapps.wallet.data.multichain.perps

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PerpsOperationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOperation(operation: PerpsOperationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: PerpsOperationStepEntity)

    @Transaction
    suspend fun insertOperationWithSteps(
        operation: PerpsOperationEntity,
        steps: List<PerpsOperationStepEntity>,
    ): Boolean {
        if (insertOperation(operation) == IGNORED_ROW_ID) {
            return false
        }
        steps.forEach { insertStep(it) }

        return true
    }

    @Query("DELETE FROM perps_operation_step WHERE operation_id = :operationId")
    suspend fun deleteSteps(operationId: String)

    @Query(
        "UPDATE perps_operation SET plan_digest = :planDigest, " +
            "expected_base_size = :expectedBaseSize, updated_at = :updatedAt " +
            "WHERE operation_id = :operationId",
    )
    suspend fun updateOperationPlan(
        operationId: String,
        planDigest: String?,
        expectedBaseSize: String?,
        updatedAt: Long,
    )

    @Transaction
    suspend fun replacePlan(
        operationId: String,
        planDigest: String?,
        expectedBaseSize: String?,
        steps: List<PerpsOperationStepEntity>,
        updatedAt: Long,
    ) {
        deleteSteps(operationId)
        steps.forEach { insertStep(it) }
        updateOperationPlan(operationId, planDigest, expectedBaseSize, updatedAt)
    }

    @Query(
        "UPDATE perps_operation SET status = :status, error = :error, updated_at = :updatedAt " +
            "WHERE operation_id = :operationId",
    )
    suspend fun updateOperation(operationId: String, status: String, error: String?, updatedAt: Long)

    @Query(
        "UPDATE perps_operation_step SET attempt_id = :attemptId, nonce = :nonce, " +
            "account_index = :accountIndex, tx_type = :txType, tx_hash = :txHash, tx_info = :txInfo, " +
            "plan_id = :planId, tx_expiry = :txExpiry, order_refs = :orderRefs, status = 'signed', " +
            "updated_at = :updatedAt WHERE operation_id = :operationId AND step_id = :stepId",
    )
    suspend fun updateStepSigned(
        operationId: String,
        stepId: String,
        attemptId: String,
        nonce: Long,
        accountIndex: Long,
        txType: Int,
        txHash: String,
        txInfo: String,
        planId: String,
        txExpiry: Long,
        orderRefs: String,
        updatedAt: Long,
    )

    @Query(
        "UPDATE perps_operation_step SET status = :status, tx_hash = COALESCE(:txHash, tx_hash), " +
            "error = COALESCE(:error, error), updated_at = :updatedAt " +
            "WHERE operation_id = :operationId AND step_id = :stepId",
    )
    suspend fun updateStepStatus(
        operationId: String,
        stepId: String,
        status: String,
        txHash: String?,
        error: String?,
        updatedAt: Long,
    )

    @Transaction
    suspend fun resolveStep(
        operationId: String,
        stepId: String,
        stepStatus: String,
        txHash: String?,
        error: String?,
        operationStatus: String,
        updatedAt: Long,
    ) {
        updateStepStatus(
            operationId = operationId,
            stepId = stepId,
            status = stepStatus,
            txHash = txHash,
            error = error,
            updatedAt = updatedAt,
        )
        updateOperation(
            operationId = operationId,
            status = operationStatus,
            error = error,
            updatedAt = updatedAt,
        )
    }

    @Query("SELECT * FROM perps_operation WHERE wallet_id = :walletId ORDER BY created_at DESC")
    suspend fun getOperations(walletId: String): List<PerpsOperationEntity>

    @Query("SELECT * FROM perps_operation WHERE wallet_id = :walletId ORDER BY created_at DESC")
    fun observeOperations(walletId: String): Flow<List<PerpsOperationEntity>>

    @Query(
        "SELECT step.* FROM perps_operation_step AS step " +
            "INNER JOIN perps_operation AS operation ON operation.operation_id = step.operation_id " +
            "WHERE operation.wallet_id = :walletId ORDER BY step.submitted_at ASC",
    )
    suspend fun getSteps(walletId: String): List<PerpsOperationStepEntity>

    @Query(
        "SELECT * FROM perps_operation WHERE wallet_id = :walletId " +
            "AND status IN ('in_progress', 'unknown') ORDER BY created_at DESC",
    )
    suspend fun getOpenOperations(walletId: String): List<PerpsOperationEntity>
}

private const val IGNORED_ROW_ID = -1L
