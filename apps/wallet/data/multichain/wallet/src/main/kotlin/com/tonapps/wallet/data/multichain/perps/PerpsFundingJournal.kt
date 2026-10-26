package com.tonapps.wallet.data.multichain.perps

import com.tonapps.async.Async
import com.tonapps.log.L
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class PerpsFundingKind(val value: String) {
    DEPOSIT("deposit"),
    WITHDRAW("withdraw");

    companion object {

        fun of(value: String): PerpsFundingKind = entries.firstOrNull { it.value == value } ?: DEPOSIT
    }
}

enum class PerpsDepositRoute(val value: String) {
    DIRECT_GATEWAY("direct_gateway"),
    CROSSCHAIN_SWAP("crosschain_swap");

    companion object {

        fun of(value: String): PerpsDepositRoute = entries.firstOrNull { it.value == value } ?: CROSSCHAIN_SWAP
    }
}

enum class PerpsDepositStage(val value: String) {
    PREPARING("preparing"),
    SOURCE_SIGNED("source_signed"),
    SOURCE_SENDING("source_sending"),
    SOURCE_UNKNOWN("source_unknown"),
    AWAITING_DELIVERY("awaiting_delivery"),
    REGISTRATION_PENDING("registration_pending"),
    REGISTERED_PENDING("registered_pending"),
    NEEDS_AUTHORIZATION("needs_authorization"),
    CREDITED("credited"),
    UNCONFIRMED("unconfirmed"),
    FAILED("failed"),
    REFUNDED("refunded"),
    ACCEPTED("accepted"),
    AWAITING_L1("awaiting_l1"),
    SETTLED("settled");

    val isBeforeSideEffect: Boolean
        get() = this == PREPARING

    val isOutcome: Boolean
        get() = this == CREDITED || this == FAILED || this == REFUNDED || this == SETTLED

    val isSucceeded: Boolean
        get() = this == CREDITED || this == ACCEPTED || this == AWAITING_L1 || this == SETTLED

    val isSubmitting: Boolean
        get() = this == SOURCE_SIGNED || this == SOURCE_SENDING || this == SOURCE_UNKNOWN

    val isTrackingInBackground: Boolean
        get() = this == ACCEPTED || this == AWAITING_L1

    val isStalled: Boolean
        get() = this == UNCONFIRMED

    val isUnresolved: Boolean
        get() = !isBeforeSideEffect && !isOutcome && !isStalled

    val holdsUnregisteredPayment: Boolean
        get() = this == SOURCE_SIGNED || this == SOURCE_SENDING || this == SOURCE_UNKNOWN || this == AWAITING_DELIVERY

    companion object {

        fun of(value: String): PerpsDepositStage = entries.firstOrNull { it.value == value } ?: SOURCE_UNKNOWN
    }
}

enum class PerpsFundingStepKind {
    APPROVAL,
    MAIN,
}

enum class PerpsFundingStepStatus {
    SIGNED,
    SENDING,
    SENT,
    UNKNOWN,
    FAILED,
}

@Serializable
data class PerpsFundingStep(
    val stepId: String,
    val kind: PerpsFundingStepKind,
    val chainId: String,
    val to: String,
    val value: String,
    val data: String?,
    val txHash: String?,
    val signedData: String,
    val nonce: String?,
    val status: PerpsFundingStepStatus,
) {
    val isUnresolved: Boolean
        get() = status == PerpsFundingStepStatus.SIGNED ||
            status == PerpsFundingStepStatus.SENDING ||
            status == PerpsFundingStepStatus.UNKNOWN
}

@Serializable
data class PerpsDepositRegistration(
    val operationId: String,
    val assetId: String,
    val amount: String,
    val l1TxHash: String?,
    val executionId: String?,
)

data class PerpsFundingRecord(
    val operationId: String,
    val walletId: String,
    val kind: PerpsFundingKind,
    val stage: PerpsDepositStage,
    val route: PerpsDepositRoute,
    val beneficiary: String,
    val sourceAssetId: String,
    val sourceSymbol: String,
    val sourceDecimals: Int,
    val sourceAmount: String,
    val targetAmount: String,
    val quoteId: String?,
    val executionId: String?,
    val sourceTxHash: String?,
    val l1TxHash: String?,
    val steps: List<PerpsFundingStep>,
    val stepsReadable: Boolean = true,
    val linkSignature: String?,
    val linkDeadline: Long?,
    val registration: PerpsDepositRegistration?,
    val depositId: String?,
    val creditedAmount: String?,
    val accountIndex: Long?,
    val failureReason: String?,
    val revision: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

class PerpsFundingRevisionException(operationId: String) :
    IllegalStateException("perps funding: $operationId was changed by another writer")

class PerpsFundingUnreadableException(operationId: String) :
    IllegalStateException("perps funding: $operationId holds steps this build cannot read")

class PerpsFundingJournal(
    private val dao: PerpsFundingDao,
) {

    private val mutex = Mutex()

    fun observe(walletId: String): Flow<List<PerpsFundingRecord>> {
        return dao.observeOperations(walletId).map { operations -> operations.map { it.toRecord() } }
    }

    suspend fun list(walletId: String): List<PerpsFundingRecord> = withContext(Async.Io) {
        dao.getOperations(walletId).map { it.toRecord() }
    }

    suspend fun saveIfAbsent(record: PerpsFundingRecord): Boolean = withContext(Async.Io) {
        mutex.withLock { dao.insert(record.toEntity()) != IGNORED_ROW_ID }
    }

    suspend fun update(
        operationId: String,
        expectedRevision: Int,
        transform: (PerpsFundingRecord) -> PerpsFundingRecord,
    ): PerpsFundingRecord = withContext(Async.Io) {
        mutex.withLock {
            val current = dao.get(operationId)?.toRecord()
                ?: throw IllegalStateException("perps funding: $operationId is not in the journal")
            if (current.revision != expectedRevision) {
                throw PerpsFundingRevisionException(operationId)
            }
            if (!current.stepsReadable) {
                throw PerpsFundingUnreadableException(operationId)
            }
            val updated = transform(current)
            require(
                updated.operationId == current.operationId &&
                    updated.walletId == current.walletId &&
                    updated.kind == current.kind,
            ) { "perps funding: identity of $operationId changed" }
            if (updated == current) {
                return@withLock current
            }
            val next = updated.copy(
                revision = current.revision + 1,
                updatedAt = System.currentTimeMillis(),
            )
            if (dao.update(next.toEntity()) == 0) {
                throw PerpsFundingRevisionException(operationId)
            }
            next
        }
    }

    suspend fun delete(operationIds: List<String>): Unit = withContext(Async.Io) {
        mutex.withLock { dao.delete(operationIds) }
    }

    suspend fun cleanup(now: Long = System.currentTimeMillis()): Unit = withContext(Async.Io) {
        mutex.withLock {
            val expired = dao.getAll().map { it.toRecord() }.filter { record ->
                when {
                    record.stage.isStalled -> record.updatedAt < now - STALLED_RETENTION_MS
                    record.stage.isUnresolved -> false
                    else -> record.updatedAt < now - SETTLED_RETENTION_MS
                }
            }
            if (expired.isNotEmpty()) {
                dao.delete(expired.map { it.operationId })
            }
        }
    }

    private companion object {
        const val IGNORED_ROW_ID = -1L
        const val SETTLED_RETENTION_MS = 24L * 60L * 60L * 1_000L
        const val STALLED_RETENTION_MS = 30L * 24L * 60L * 60L * 1_000L
    }
}

private val fundingJson = Json { ignoreUnknownKeys = true }

private fun PerpsFundingEntity.toRecord(): PerpsFundingRecord {
    val steps = stepsJson.decodeStepsOrNull()
    return PerpsFundingRecord(
        operationId = operationId,
        walletId = walletId,
        kind = PerpsFundingKind.of(kind),
        stage = PerpsDepositStage.of(stage),
        route = PerpsDepositRoute.of(route),
        beneficiary = beneficiary,
        sourceAssetId = sourceAssetId,
        sourceSymbol = sourceSymbol,
        sourceDecimals = sourceDecimals,
        sourceAmount = sourceAmount,
        targetAmount = targetAmount,
        quoteId = quoteId,
        executionId = executionId,
        sourceTxHash = sourceTxHash,
        l1TxHash = l1TxHash,
        steps = steps.orEmpty(),
        stepsReadable = steps != null,
        linkSignature = linkSignature,
        linkDeadline = linkDeadline,
        registration = registrationJson?.let { json ->
            try {
                fundingJson.decodeFromString<PerpsDepositRegistration>(json)
            } catch (e: Throwable) {
                L.e(e)
                null
            }
        },
        depositId = depositId,
        creditedAmount = creditedAmount,
        accountIndex = accountIndex,
        failureReason = failureReason,
        revision = revision,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun PerpsFundingRecord.toEntity(): PerpsFundingEntity {
    return PerpsFundingEntity(
        operationId = operationId,
        walletId = walletId,
        kind = kind.value,
        stage = stage.value,
        route = route.value,
        beneficiary = beneficiary,
        sourceAssetId = sourceAssetId,
        sourceSymbol = sourceSymbol,
        sourceDecimals = sourceDecimals,
        sourceAmount = sourceAmount,
        targetAmount = targetAmount,
        quoteId = quoteId,
        executionId = executionId,
        sourceTxHash = sourceTxHash,
        l1TxHash = l1TxHash,
        stepsJson = fundingJson.encodeToString(steps),
        linkSignature = linkSignature,
        linkDeadline = linkDeadline,
        registrationJson = registration?.let { fundingJson.encodeToString(it) },
        depositId = depositId,
        creditedAmount = creditedAmount,
        accountIndex = accountIndex,
        failureReason = failureReason,
        revision = revision,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun String.decodeStepsOrNull(): List<PerpsFundingStep>? {
    if (isBlank()) {
        return emptyList()
    }
    return try {
        fundingJson.decodeFromString<List<PerpsFundingStep>>(this)
    } catch (e: Throwable) {
        L.e(e)
        null
    }
}
