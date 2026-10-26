package com.tonapps.perps.trade

import com.tonapps.chainkit.lighter.trading.PerpsExecutionDelegate
import com.tonapps.chainkit.lighter.trading.PerpsExecutionErrorKind
import com.tonapps.chainkit.lighter.trading.PerpsExecutionException
import com.tonapps.chainkit.lighter.trading.PerpsExecutionNonce
import com.tonapps.chainkit.lighter.trading.PerpsSignedStep
import com.tonapps.chainkit.lighter.trading.PerpsSubmissionOutcome
import com.tonapps.chainkit.perpetual.PerpsScope
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.PerpsSendError
import com.tonapps.perps.data.PerpsSendReason
import com.tonapps.perps.data.PerpsSignedTx
import com.tonapps.perps.data.toExecutionErrorKind
import com.tonapps.wallet.data.multichain.perps.PerpsOperationJournal
import com.tonapps.wallet.data.multichain.perps.PerpsOrderRef
import com.tonapps.wallet.data.multichain.perps.PerpsStepStatus

internal class PerpsExecutionRelay(
    private val repository: PerpsRepository,
    private val journal: PerpsOperationJournal,
    private val nonces: PerpsNonceCoordinator,
) : PerpsExecutionDelegate {

    override suspend fun currentTimeUnixMs(): Long = System.currentTimeMillis()

    override suspend fun nextNonce(scope: PerpsScope): PerpsExecutionNonce {
        return PerpsExecutionNonce(nonces.next(scope) { fetchNonce(scope) })
    }

    override suspend fun resyncNonce(scope: PerpsScope) {
        nonces.resync(scope) { fetchNonce(scope) }
    }

    override suspend fun persistSigned(step: PerpsSignedStep) {
        journal.updateStepSigned(
            operationId = step.operationId,
            stepId = step.stepId,
            attemptId = step.attemptId,
            nonce = step.nonce,
            accountIndex = step.scope.accountIndex,
            txType = step.txType,
            txHash = step.txHash,
            txInfo = step.txInfo,
            planId = step.planId,
            txExpiry = step.transactionExpiryUnixMs,
            orderRefs = step.orders.map { PerpsOrderRef(it.role.name, it.clientOrderIndex) },
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun markStepSending(step: PerpsSignedStep) {
        mark(step, PerpsStepStatus.SENDING)
    }

    override suspend fun markStepAccepted(step: PerpsSignedStep) {
        mark(step, PerpsStepStatus.SENT)
    }

    override suspend fun markStepUnknown(step: PerpsSignedStep) {
        mark(step, PerpsStepStatus.UNKNOWN)
    }

    override suspend fun submit(step: PerpsSignedStep): PerpsSubmissionOutcome {
        return submitTransaction(
            walletId = step.scope.walletId,
            txType = step.txType,
            txInfo = step.txInfo,
            txHash = step.txHash,
        )
    }

    suspend fun submitTransaction(
        walletId: String,
        txType: Int,
        txInfo: String,
        txHash: String,
    ): PerpsSubmissionOutcome {
        val tx = PerpsSignedTx(txType = txType, txInfo = txInfo, txHash = txHash)

        return when (val result = repository.sendTransactions(walletId, listOf(tx))) {
            is KResult.Ok -> {
                val sent = result.value.singleOrNull()
                val hash = normalizeHash(sent?.txHash.orEmpty())
                val expected = normalizeHash(txHash)
                if (sent?.txType == txType && hash.isNotEmpty() && hash == expected) {
                    PerpsSubmissionOutcome.Accepted
                } else {
                    PerpsSubmissionOutcome.ProtocolFailure
                }
            }

            is KResult.Err -> refusal(result.value)
        }
    }

    private fun refusal(error: PerpsSendError): PerpsSubmissionOutcome {
        return when (error) {
            is PerpsSendError.Refused -> when (error.reason) {
                PerpsSendReason.RESIGN_REQUIRED -> PerpsSubmissionOutcome.ResignRequired

                PerpsSendReason.ORDER_GONE -> throw PerpsExecutionException(
                    PerpsExecutionErrorKind.Unknown,
                    error.code,
                )

                else -> throw PerpsExecutionException(
                    PerpsExecutionErrorKind.ServerRejected,
                    "${error.reason.name}:${error.code}",
                )
            }

            PerpsSendError.Unknown -> throw PerpsExecutionException(
                PerpsExecutionErrorKind.ServerUnavailable,
                "the send outcome is unknown",
            )

            is PerpsSendError.Failed -> throw PerpsExecutionException(
                error.error.toExecutionErrorKind(),
                "the send failed",
            )
        }
    }

    private suspend fun fetchNonce(scope: PerpsScope): Long {
        return when (val result = repository.nextNonce(scope.walletId)) {
            is KResult.Ok -> result.value.nonce
            is KResult.Err -> throw PerpsExecutionException(
                result.value.toExecutionErrorKind(),
                "the nonce is unavailable",
            )
        }
    }

    private suspend fun mark(step: PerpsSignedStep, status: PerpsStepStatus) {
        journal.updateStepStatus(
            operationId = step.operationId,
            stepId = step.stepId,
            status = status,
            updatedAt = System.currentTimeMillis(),
        )
    }
}

private fun normalizeHash(hash: String): String {
    return hash.trim().lowercase().removePrefix("0x")
}
