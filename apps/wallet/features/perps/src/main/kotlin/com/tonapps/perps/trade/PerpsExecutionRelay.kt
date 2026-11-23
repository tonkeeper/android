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
import com.tonapps.perps.data.PerpsSentTx
import com.tonapps.perps.data.PerpsSignedTx
import com.tonapps.perps.data.toExecutionErrorKind
import com.tonapps.wallet.data.multichain.perps.PerpsOperationJournal
import com.tonapps.wallet.data.multichain.perps.PerpsOrderRef
import com.tonapps.wallet.data.multichain.perps.PerpsStepStatus
import java.util.concurrent.ConcurrentHashMap

internal class PerpsExecutionRelay(
    private val repository: PerpsRepository,
    private val journal: PerpsOperationJournal,
    private val nonces: PerpsNonceCoordinator,
) : PerpsExecutionDelegate {

    private data class Owner(val walletId: String, val accountIndex: Long)

    private val refusedSteps = ConcurrentHashMap<Owner, String>()

    private val noncesToReuse = ConcurrentHashMap<Owner, Long>()

    private val retryNonces = ConcurrentHashMap<Owner, Long>()

    // chainkit re-signs a refused step with its now and its expiry both from here, and checks the
    // snapshots' receipts, stamped on the device clock, against that now: it stays the device's.
    override suspend fun currentTimeUnixMs(): Long = System.currentTimeMillis()

    override suspend fun nextNonce(scope: PerpsScope): PerpsExecutionNonce {
        val retryNonce = retryNonces.remove(scope.owner())
        if (retryNonce != null) {
            return PerpsExecutionNonce(retryNonce)
        }

        return PerpsExecutionNonce(nonces.next(scope) { fetchNonce(scope) })
    }

    override suspend fun resyncNonce(scope: PerpsScope) {
        val refusedNonce = noncesToReuse.remove(scope.owner())
        if (refusedNonce != null) {
            retryNonces[scope.owner()] = refusedNonce
        } else {
            nonces.resync(scope) { fetchNonce(scope) }
        }
    }

    suspend fun catchUpNonce(scope: PerpsScope) {
        retryNonces.remove(scope.owner())
        noncesToReuse.remove(scope.owner())
        nonces.catchUp(scope) { fetchNonce(scope) }
    }

    suspend fun markNonceAccepted(scope: PerpsScope, nonce: Long) {
        nonces.markAccepted(scope, nonce)
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
        val stepKey = "${step.operationId}/${step.stepId}"
        val owner = step.scope.owner()
        noncesToReuse.remove(owner)
        val earlier = refusedSteps.remove(owner)?.takeIf { it == stepKey }
        val result = send(
            walletId = step.scope.walletId,
            txType = step.txType,
            txInfo = step.txInfo,
            txHash = step.txHash,
        )
        if (earlier == null && result is KResult.Err && result.value.leavesNonceUnconsumed()) {
            nonces.returnUnconsumed(step.scope, step.nonce)
        }
        val outcome = try {
            outcome(result, step.txType, step.txHash)
        } catch (e: PerpsExecutionException) {
            if (earlier == null) {
                throw e
            }
            nonces.forget(step.scope)
            throw PerpsExecutionException(
                PerpsExecutionErrorKind.SubmitUnknown,
                "the retry of a refused step failed: ${e.message}",
            )
        }
        if (outcome == PerpsSubmissionOutcome.Accepted) {
            nonces.markAccepted(step.scope, step.nonce)
        }
        if (outcome != PerpsSubmissionOutcome.ResignRequired) {
            return outcome
        }
        if (earlier != null) {
            nonces.forget(step.scope)
            throw PerpsExecutionException(
                PerpsExecutionErrorKind.SubmitUnknown,
                "the venue refused the same nonce twice",
            )
        }
        refusedSteps[owner] = stepKey
        noncesToReuse[owner] = step.nonce

        return outcome
    }

    suspend fun submitTransaction(
        walletId: String,
        txType: Int,
        txInfo: String,
        txHash: String,
    ): PerpsSubmissionOutcome {
        return outcome(send(walletId, txType, txInfo, txHash), txType, txHash)
    }

    private suspend fun send(
        walletId: String,
        txType: Int,
        txInfo: String,
        txHash: String,
    ): KResult<List<PerpsSentTx>, PerpsSendError> {
        val tx = PerpsSignedTx(txType = txType, txInfo = txInfo, txHash = txHash)

        return repository.sendTransactions(walletId, listOf(tx))
    }

    private fun outcome(
        result: KResult<List<PerpsSentTx>, PerpsSendError>,
        txType: Int,
        txHash: String,
    ): PerpsSubmissionOutcome {
        return when (result) {
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

    private fun PerpsScope.owner(): Owner = Owner(walletId, accountIndex)

    private fun PerpsSendError.leavesNonceUnconsumed(): Boolean {
        return when (this) {
            is PerpsSendError.Refused -> reason != PerpsSendReason.RESIGN_REQUIRED
            is PerpsSendError.Failed -> true
            PerpsSendError.Unknown -> false
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
