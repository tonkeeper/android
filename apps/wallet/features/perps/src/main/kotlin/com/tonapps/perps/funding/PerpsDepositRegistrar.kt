package com.tonapps.perps.funding

import com.tonapps.log.L
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.wallet.data.multichain.perps.PerpsDepositRegistration
import com.tonapps.wallet.data.multichain.perps.PerpsDepositStage
import com.tonapps.wallet.data.multichain.perps.PerpsFundingJournal
import com.tonapps.wallet.data.multichain.perps.PerpsFundingRecord
import io.perpsapi.models.Deposit
import io.perpsapi.models.OpenDepositRequest
import kotlinx.coroutines.CancellationException

class PerpsDepositRegistrar(
    private val repository: PerpsRepository,
    private val journal: PerpsFundingJournal,
) {

    suspend fun register(record: PerpsFundingRecord): PerpsFundingRecord {
        check(record.stage == PerpsDepositStage.REGISTRATION_PENDING) {
            "perps funding: ${record.operationId} is not awaiting registration"
        }
        val l1TxHash = record.l1TxHash?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        check(l1TxHash != null || !record.executionId.isNullOrBlank()) {
            "perps funding: ${record.operationId} has neither an L1 hash nor an execution id"
        }
        val registration = record.registration ?: sign(record, l1TxHash)

        val deposit = try {
            repository.openDeposit(record.walletId, registration.toRequest())
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            val error = e.toPerpsFundingError()
            if (error is PerpsFundingError.Refused && error.needsAuthorization) {
                L.e(e, "perps funding: registration of ${record.operationId} refused (${error.code})")
                return journal.update(record.operationId, record.revision) { current ->
                    current.copy(
                        registration = registration,
                        stage = PerpsDepositStage.NEEDS_AUTHORIZATION,
                        failureReason = error.code,
                    )
                }
            }
            throw e
        }

        validate(deposit, record.operationId, l1TxHash, expectedDepositId = null)
        return journal.update(record.operationId, record.revision) { current ->
            current.copy(
                registration = registration,
                depositId = deposit.depositId,
                stage = stageOf(deposit, whenPending = PerpsDepositStage.REGISTERED_PENDING),
                creditedAmount = deposit.creditedAmount ?: current.creditedAmount,
                accountIndex = deposit.accountIndex ?: current.accountIndex,
            )
        }
    }

    suspend fun refresh(record: PerpsFundingRecord): PerpsFundingRecord {
        check(record.stage == PerpsDepositStage.REGISTERED_PENDING) {
            "perps funding: ${record.operationId} is not registered"
        }
        val depositId = record.depositId
            ?: throw IllegalStateException("perps funding: ${record.operationId} has no deposit id")
        val deposit = repository.getDeposit(record.walletId, depositId)
        validate(deposit, record.operationId, record.l1TxHash, expectedDepositId = depositId)
        return journal.update(record.operationId, record.revision) { current ->
            current.copy(
                stage = stageOf(deposit, whenPending = current.stage),
                creditedAmount = deposit.creditedAmount ?: current.creditedAmount,
                accountIndex = deposit.accountIndex ?: current.accountIndex,
            )
        }
    }

    private fun sign(record: PerpsFundingRecord, l1TxHash: String?): PerpsDepositRegistration {
        val amount = PerpsFundingAmount.declaredAmount(
            units = PerpsFundingAmount.integerUnits(record.targetAmount),
            decimals = PerpsFundingAmount.USDC_DECIMALS,
        )
        val executionId = record.executionId?.trim().orEmpty()
        val assetId = record.sourceAssetId.trim()

        return PerpsDepositRegistration(
            operationId = record.operationId,
            assetId = assetId,
            amount = amount,
            l1TxHash = l1TxHash,
            executionId = executionId.ifEmpty { null },
        )
    }

    private fun validate(deposit: Deposit, operationId: String, expectedTxHash: String?, expectedDepositId: String?) {
        val depositId = deposit.depositId?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("perps funding: $operationId was recorded without a deposit id")
        if (expectedDepositId != null && depositId != expectedDepositId) {
            throw IllegalStateException("perps funding: $operationId answered with another deposit")
        }
        val recordedHash = deposit.l1TxHash
        if (expectedTxHash != null && recordedHash != null && !recordedHash.equals(expectedTxHash, ignoreCase = true)) {
            throw IllegalStateException("perps funding: $operationId answered with another transaction")
        }
    }

    private fun stageOf(deposit: Deposit, whenPending: PerpsDepositStage): PerpsDepositStage {
        return when (deposit.status) {
            Deposit.Status.credited -> PerpsDepositStage.CREDITED
            Deposit.Status.unfunded -> PerpsDepositStage.UNCONFIRMED
            Deposit.Status.failed, Deposit.Status.expired -> PerpsDepositStage.FAILED
            Deposit.Status.refunded -> PerpsDepositStage.REFUNDED
            Deposit.Status.pending, Deposit.Status.unknown, null -> whenPending
        }
    }

    private fun PerpsDepositRegistration.toRequest(): OpenDepositRequest {
        return OpenDepositRequest(
            operationId = operationId,
            assetId = assetId,
            amount = amount,
            l1TxHash = l1TxHash,
            executionId = executionId,
        )
    }

    private companion object {
        const val MILLIS_IN_SECOND = 1_000L
    }
}
