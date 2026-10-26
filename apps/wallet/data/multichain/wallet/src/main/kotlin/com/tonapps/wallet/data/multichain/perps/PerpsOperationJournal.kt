package com.tonapps.wallet.data.multichain.perps

import com.tonapps.async.Async
import com.tonapps.log.L
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class PerpsOperationStatus(val value: String) {
    IN_PROGRESS("in_progress"),
    SUCCEEDED("succeeded"),
    PARTIALLY_FILLED("partially_filled"),
    FAILED("failed"),
    UNKNOWN("unknown");

    companion object {

        fun of(value: String): PerpsOperationStatus {
            return entries.firstOrNull { it.value == value } ?: UNKNOWN
        }
    }
}

enum class PerpsStepStatus(val value: String) {
    PLANNED("planned"),
    SIGNED("signed"),
    SENDING("sending"),
    SENT("sent"),
    ACCEPTED("accepted"),
    DEAD("dead"),
    UNKNOWN("unknown");

    val isTerminal: Boolean
        get() = this == ACCEPTED || this == DEAD

    companion object {

        fun of(value: String): PerpsStepStatus {
            return entries.firstOrNull { it.value == value } ?: UNKNOWN
        }
    }
}

enum class PerpsStepKind {
    LEVERAGE,
    OPEN,
    CLOSE,
    ADD,
    AUTO_CLOSE,
    MARGIN,
    ORDER_CANCEL,
    ORDER_MODIFY;

    companion object {

        fun of(value: String): PerpsStepKind {
            return entries.firstOrNull { it.name == value } ?: OPEN
        }
    }
}

enum class PerpsOperationKind {
    OPEN,
    CLOSE,
    REDUCE,
    ADD,
    MARGIN_ADD,
    MARGIN_REMOVE,
    AUTO_CLOSE,
    OPEN_LIMIT,
    ORDER_CANCEL,
    ORDER_MODIFY;

    companion object {

        fun of(value: String): PerpsOperationKind {
            return entries.firstOrNull { it.name == value } ?: OPEN
        }
    }
}

@Serializable
data class PerpsOrderRef(
    val role: String,
    val clientOrderIndex: Long,
)

data class PerpsOperationRecord(
    val operationId: String,
    val walletId: String,
    val marketIndex: Int,
    val kind: PerpsOperationKind,
    val side: String,
    val marginQuote: String,
    val leverage: Int,
    val reviewJson: String?,
    val planDigest: String?,
    val expectedBaseSize: String?,
    val baseBefore: String?,
    val marginBefore: String?,
    val status: PerpsOperationStatus,
    val error: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class PerpsStepRecord(
    val operationId: String,
    val stepId: String,
    val kind: PerpsStepKind,
    val requiresStepId: String?,
    val attemptId: String,
    val apiKeyIndex: Int,
    val nonce: Long?,
    val accountIndex: Long?,
    val txType: Int?,
    val txHash: String?,
    val txInfo: String?,
    val clientOrderIndex: Long,
    val planId: String?,
    val txExpiry: Long?,
    val orderRefs: List<PerpsOrderRef>,
    val submittedAt: Long,
    val status: PerpsStepStatus,
    val error: String?,
    val updatedAt: Long,
)

class PerpsOperationJournal(
    private val dao: PerpsOperationDao,
) {

    suspend fun getOperations(walletId: String): List<PerpsOperationRecord> = withContext(Async.Io) {
        dao.getOperations(walletId).map { it.toRecord() }
    }

    fun observeOperations(walletId: String): Flow<List<PerpsOperationRecord>> {
        return dao.observeOperations(walletId).map { operations -> operations.map { it.toRecord() } }
    }

    suspend fun getOpenOperations(walletId: String): List<PerpsOperationRecord> = withContext(Async.Io) {
        dao.getOpenOperations(walletId).map { it.toRecord() }
    }

    suspend fun getSteps(walletId: String): List<PerpsStepRecord> = withContext(Async.Io) {
        dao.getSteps(walletId).map { it.toRecord() }
    }

    suspend fun insertOperationWithSteps(
        operation: PerpsOperationRecord,
        steps: List<PerpsStepRecord>,
    ): Boolean = withContext(Async.Io) {
        dao.insertOperationWithSteps(operation.toEntity(), steps.map { it.toEntity() })
    }

    suspend fun replacePlan(
        operationId: String,
        planDigest: String?,
        expectedBaseSize: String?,
        steps: List<PerpsStepRecord>,
        updatedAt: Long,
    ) = withContext(Async.Io) {
        dao.replacePlan(
            operationId = operationId,
            planDigest = planDigest,
            expectedBaseSize = expectedBaseSize,
            steps = steps.map { it.toEntity() },
            updatedAt = updatedAt,
        )
    }

    suspend fun updateOperation(
        operationId: String,
        status: PerpsOperationStatus,
        error: String?,
        updatedAt: Long,
    ) = withContext(Async.Io) {
        dao.updateOperation(operationId, status.value, error, updatedAt)
    }

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
        orderRefs: List<PerpsOrderRef>,
        updatedAt: Long,
    ) = withContext(Async.Io) {
        dao.updateStepSigned(
            operationId = operationId,
            stepId = stepId,
            attemptId = attemptId,
            nonce = nonce,
            accountIndex = accountIndex,
            txType = txType,
            txHash = txHash,
            txInfo = txInfo,
            planId = planId,
            txExpiry = txExpiry,
            orderRefs = journalJson.encodeToString(orderRefs),
            updatedAt = updatedAt,
        )
    }

    suspend fun resolveStep(
        operationId: String,
        stepId: String,
        stepStatus: PerpsStepStatus,
        operationStatus: PerpsOperationStatus,
        txHash: String? = null,
        error: String? = null,
        updatedAt: Long,
    ) = withContext(Async.Io) {
        dao.resolveStep(
            operationId = operationId,
            stepId = stepId,
            stepStatus = stepStatus.value,
            txHash = txHash,
            error = error,
            operationStatus = operationStatus.value,
            updatedAt = updatedAt,
        )
    }

    suspend fun updateStepStatus(
        operationId: String,
        stepId: String,
        status: PerpsStepStatus,
        txHash: String? = null,
        error: String? = null,
        updatedAt: Long,
    ) = withContext(Async.Io) {
        dao.updateStepStatus(
            operationId = operationId,
            stepId = stepId,
            status = status.value,
            txHash = txHash,
            error = error,
            updatedAt = updatedAt,
        )
    }
}

private fun PerpsOperationEntity.toRecord(): PerpsOperationRecord {
    return PerpsOperationRecord(
        operationId = operationId,
        walletId = walletId,
        marketIndex = marketIndex,
        kind = PerpsOperationKind.of(kind),
        side = side,
        marginQuote = marginQuote,
        leverage = leverage,
        reviewJson = reviewJson,
        planDigest = planDigest,
        expectedBaseSize = expectedBaseSize,
        baseBefore = baseBefore,
        marginBefore = marginBefore,
        status = PerpsOperationStatus.of(status),
        error = error,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun PerpsOperationRecord.toEntity(): PerpsOperationEntity {
    return PerpsOperationEntity(
        operationId = operationId,
        walletId = walletId,
        marketIndex = marketIndex,
        kind = kind.name,
        side = side,
        marginQuote = marginQuote,
        leverage = leverage,
        reviewJson = reviewJson,
        planDigest = planDigest,
        expectedBaseSize = expectedBaseSize,
        baseBefore = baseBefore,
        marginBefore = marginBefore,
        status = status.value,
        error = error,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun PerpsOperationStepEntity.toRecord(): PerpsStepRecord {
    return PerpsStepRecord(
        operationId = operationId,
        stepId = stepId,
        kind = PerpsStepKind.of(kind),
        requiresStepId = requiresStepId,
        attemptId = attemptId,
        apiKeyIndex = apiKeyIndex,
        nonce = nonce,
        accountIndex = accountIndex,
        txType = txType,
        txHash = txHash,
        txInfo = txInfo,
        clientOrderIndex = clientOrderIndex,
        planId = planId,
        txExpiry = txExpiry,
        orderRefs = orderRefs.toOrderRefs(),
        submittedAt = submittedAt,
        status = PerpsStepStatus.of(status),
        error = error,
        updatedAt = updatedAt,
    )
}

private fun PerpsStepRecord.toEntity(): PerpsOperationStepEntity {
    return PerpsOperationStepEntity(
        operationId = operationId,
        stepId = stepId,
        kind = kind.name,
        requiresStepId = requiresStepId,
        attemptId = attemptId,
        apiKeyIndex = apiKeyIndex,
        nonce = nonce,
        accountIndex = accountIndex,
        txType = txType,
        txHash = txHash,
        txInfo = txInfo,
        clientOrderIndex = clientOrderIndex,
        planId = planId,
        txExpiry = txExpiry,
        orderRefs = journalJson.encodeToString(orderRefs),
        submittedAt = submittedAt,
        status = status.value,
        error = error,
        updatedAt = updatedAt,
    )
}

private val journalJson = Json { ignoreUnknownKeys = true }

private fun String?.toOrderRefs(): List<PerpsOrderRef> {
    val payload = this?.takeIf { it.isNotBlank() } ?: return emptyList()
    return try {
        journalJson.decodeFromString<List<PerpsOrderRef>>(payload)
    } catch (e: Throwable) {
        L.e(e)
        emptyList()
    }
}
