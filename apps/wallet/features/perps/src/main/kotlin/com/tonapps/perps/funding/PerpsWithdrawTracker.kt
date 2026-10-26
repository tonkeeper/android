package com.tonapps.perps.funding

import com.tonapps.log.L
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.PerpsAccountRefresh
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.PerpsSendError
import com.tonapps.perps.data.PerpsSendReason
import com.tonapps.perps.data.PerpsSignedTx
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.perps.PerpsDepositRoute
import com.tonapps.wallet.data.multichain.perps.PerpsDepositStage
import com.tonapps.wallet.data.multichain.perps.PerpsFundingJournal
import com.tonapps.wallet.data.multichain.perps.PerpsFundingRecord
import com.tonapps.wallet.data.multichain.perps.PerpsFundingStep
import com.tonapps.wallet.data.multichain.perps.PerpsFundingStepStatus
import io.perpsapi.models.ActivityItem
import io.perpsapi.models.FundingStatus
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class PerpsWithdrawTracker(
    private val repository: PerpsRepository,
    private val journal: PerpsFundingJournal,
    private val mcAccountRepository: McAccountRepository,
    private val lighterApi: LighterPublicApi,
    private val accountRefresh: PerpsAccountRefresh,
) {

    private val lastTrackedAt = ConcurrentHashMap<String, Long>()
    private val executedAtByOperation = ConcurrentHashMap<String, Long>()

    suspend fun advance(record: PerpsFundingRecord): PerpsFundingRecord? {
        return when (record.stage) {
            PerpsDepositStage.SOURCE_SIGNED -> send(record, firstAttempt = false).record
            PerpsDepositStage.SOURCE_SENDING,
            PerpsDepositStage.SOURCE_UNKNOWN -> settleAmbiguous(record)
            PerpsDepositStage.ACCEPTED -> if (record.route == PerpsDepositRoute.CROSSCHAIN_SWAP) {
                trackSwap(record)
            } else {
                awaitL1(record)
            }
            PerpsDepositStage.AWAITING_L1 -> trackL1(record)
            else -> null
        }
    }

    suspend fun send(record: PerpsFundingRecord, firstAttempt: Boolean): PerpsWithdrawSend {
        val step = record.steps.firstOrNull()
            ?: return PerpsWithdrawSend(fail(record, null, NOTHING_SIGNED), null)
        val sending = journal.update(record.operationId, record.revision) {
            it.withStep(step.stepId) { current -> current.copy(status = PerpsFundingStepStatus.SENDING) }
                .copy(stage = PerpsDepositStage.SOURCE_SENDING)
        }
        val txType = if (record.route == PerpsDepositRoute.CROSSCHAIN_SWAP) {
            TX_TYPE_TRANSFER
        } else {
            TX_TYPE_WITHDRAW
        }
        val tx = PerpsSignedTx(txType = txType, txInfo = step.signedData, txHash = step.txHash.orEmpty())
        return when (val result = repository.sendTransactions(record.walletId, listOf(tx))) {
            is KResult.Ok -> {
                val sent = result.value.singleOrNull()
                if (sent != null && sent.txType == txType && sent.txHash.normalizedHash() == tx.txHash.normalizedHash()) {
                    PerpsWithdrawSend(accept(sending, step, sent.txHash), null)
                } else {
                    L.e("perps withdraw: ${record.operationId} answered with ${sent?.txType}/${sent?.txHash}")
                    PerpsWithdrawSend(unknown(sending, step), PerpsSendError.Unknown)
                }
            }

            is KResult.Err -> when (val error = result.value) {
                is PerpsSendError.Refused -> when {
                    error.reason != PerpsSendReason.RESIGN_REQUIRED ->
                        PerpsWithdrawSend(fail(sending, step, error.failureReason()), error)

                    firstAttempt -> PerpsWithdrawSend(sending, error)
                    else -> PerpsWithdrawSend(settleConsumedNonce(sending, step), error)
                }

                PerpsSendError.Unknown,
                is PerpsSendError.Failed -> PerpsWithdrawSend(unknown(sending, step), error)
            }
        }
    }

    suspend fun refused(record: PerpsFundingRecord, error: PerpsSendError.Refused): PerpsFundingRecord {
        return fail(record, record.steps.firstOrNull(), error.failureReason())
    }

    private fun PerpsSendError.Refused.failureReason(): String = "send_refused:${reason.name}:$code"

    private suspend fun settleConsumedNonce(record: PerpsFundingRecord, step: PerpsFundingStep): PerpsFundingRecord {
        val hash = step.txHash ?: return fail(record, step, HASH_UNKNOWN)
        val presence = try {
            lighterApi.transaction(hash).presence
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e, "perps withdraw: ${record.operationId} lookup failed")
            return unknown(record, step)
        }
        return when (presence) {
            LighterTransactionPresence.KNOWN -> accept(record, step, hash)
            LighterTransactionPresence.FAILED -> fail(record, step, L2_FAILED)
            LighterTransactionPresence.UNKNOWN -> fail(record, step, NONCE_CONSUMED)
        }
    }

    private suspend fun settleAmbiguous(record: PerpsFundingRecord): PerpsFundingRecord {
        val step = record.steps.firstOrNull() ?: return fail(record, null, NOTHING_SIGNED)
        val hash = step.txHash ?: return fail(record, step, HASH_UNKNOWN)
        val presence = try {
            lighterApi.transaction(hash).presence
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e, "perps withdraw: ${record.operationId} lookup failed")
            return record
        }
        return when (presence) {
            LighterTransactionPresence.KNOWN -> accept(record, step, hash)
            LighterTransactionPresence.FAILED -> fail(record, step, L2_FAILED)
            LighterTransactionPresence.UNKNOWN -> resendUnlessExpired(record, step)
        }
    }

    private suspend fun resendUnlessExpired(record: PerpsFundingRecord, step: PerpsFundingStep): PerpsFundingRecord {
        val now = System.currentTimeMillis()
        val expiredAt = step.expiredAtMs()
        return when {
            expiredAt == null -> fail(record, step, EXPIRY_UNKNOWN)
            now > expiredAt + EXPIRY_GRACE_MS -> fail(record, step, EXPIRED)
            now - record.updatedAt < RESEND_GRACE_MS -> record
            else -> send(record, firstAttempt = false).record
        }
    }

    private suspend fun awaitL1(record: PerpsFundingRecord): PerpsFundingRecord {
        return journal.update(record.operationId, record.revision) {
            it.copy(stage = PerpsDepositStage.AWAITING_L1, failureReason = null)
        }
    }

    private suspend fun trackL1(record: PerpsFundingRecord): PerpsFundingRecord {
        val now = System.currentTimeMillis()
        val last = lastTrackedAt[record.operationId] ?: 0L
        if (now - last < TRACK_INTERVAL_MS) {
            return record
        }
        val executedAtMs = executedAtMs(record)
        val items = repository.withdrawalActivity(record.walletId, limit = ACTIVITY_PAGE_SIZE)
        lastTrackedAt[record.operationId] = now
        val match = executedAtMs?.let { items.matching(record, it) }
        L.d("perps withdraw ${record.operationId}: executed at $executedAtMs, ${items.size} item(s), match=${match?.id} status=${match?.status}")
        val status = match?.status?.substringBefore('(')?.trim()?.lowercase()
        return when {
            match != null && status in SETTLED_STATUSES -> {
                stopTracking(record)
                journal.update(record.operationId, record.revision) {
                    it.copy(stage = PerpsDepositStage.SETTLED, l1TxHash = match.l1TxHashOrNull())
                }.also { mcAccountRepository.refresh() }
            }

            match != null && status in RETURNED_STATUSES -> {
                stopTracking(record)
                fail(record, record.steps.firstOrNull(), "l1_$status", match)
                    .also { accountRefresh.notifyChanged() }
            }

            now - record.createdAt > TRACK_TIMEOUT_MS -> {
                stopTracking(record)
                journal.update(record.operationId, record.revision) {
                    it.copy(stage = PerpsDepositStage.UNCONFIRMED, failureReason = L1_UNVERIFIED)
                }
            }

            else -> record
        }
    }

    private suspend fun trackSwap(record: PerpsFundingRecord): PerpsFundingRecord {
        val executionId = record.executionId ?: return fail(record, record.steps.firstOrNull(), EXECUTION_UNKNOWN)
        val now = System.currentTimeMillis()
        val last = lastTrackedAt[record.operationId] ?: 0L
        if (now - last < TRACK_INTERVAL_MS) {
            return record
        }
        val status = repository.getFundingStatus(record.walletId, executionId)
        lastTrackedAt[record.operationId] = now
        return when (status.status) {
            FundingStatus.Status.success -> {
                stopTracking(record)
                journal.update(record.operationId, record.revision) {
                    it.copy(stage = PerpsDepositStage.SETTLED, l1TxHash = status.txHashes?.firstOrNull() ?: it.l1TxHash)
                }.also { mcAccountRepository.refresh() }
            }

            FundingStatus.Status.failure -> {
                stopTracking(record)
                fail(record, record.steps.firstOrNull(), status.details ?: SWAP_FAILED)
                    .also { accountRefresh.notifyChanged() }
            }

            FundingStatus.Status.refund -> {
                stopTracking(record)
                journal.update(record.operationId, record.revision) {
                    it.copy(stage = PerpsDepositStage.REFUNDED, failureReason = status.details)
                }.also { accountRefresh.notifyChanged() }
            }

            FundingStatus.Status.unknown -> if (now - record.createdAt > UNKNOWN_EXECUTION_TIMEOUT_MS) {
                stopTracking(record)
                fail(record, record.steps.firstOrNull(), NEVER_DELIVERED)
            } else {
                record
            }

            FundingStatus.Status.pending -> if (now - record.createdAt > TRACK_TIMEOUT_MS) {
                stopTracking(record)
                journal.update(record.operationId, record.revision) {
                    it.copy(stage = PerpsDepositStage.UNCONFIRMED, failureReason = SWAP_UNCONFIRMED)
                }
            } else {
                record
            }
        }
    }

    private suspend fun executedAtMs(record: PerpsFundingRecord): Long? {
        executedAtByOperation[record.operationId]?.let { return it }
        val hash = record.sourceTxHash ?: record.steps.firstOrNull()?.txHash ?: return null
        return lighterApi.transaction(hash).executedAtMs?.also { executedAtByOperation[record.operationId] = it }
    }

    private fun stopTracking(record: PerpsFundingRecord) {
        lastTrackedAt.remove(record.operationId)
        executedAtByOperation.remove(record.operationId)
    }

    private fun List<ActivityItem>.matching(record: PerpsFundingRecord, executedAtMs: Long): ActivityItem? {
        val amount = PerpsFundingAmount.toDecimal(
            PerpsFundingAmount.integerUnits(record.targetAmount),
            PerpsFundingAmount.USDC_DECIMALS,
        )
        return filter { item ->
            val at = item.timestamp?.toInstant()?.toEpochMilli()
            at != null && abs(at - executedAtMs) < EXECUTION_TIME_TOLERANCE_MS &&
                item.amount?.toBigDecimalOrNull()?.compareTo(amount) == 0
        }.singleOrNull()
    }

    private suspend fun accept(record: PerpsFundingRecord, step: PerpsFundingStep, acceptedHash: String): PerpsFundingRecord {
        val accepted = journal.update(record.operationId, record.revision) {
            it.withStep(step.stepId) { current -> current.copy(status = PerpsFundingStepStatus.SENT) }
                .copy(stage = PerpsDepositStage.ACCEPTED, sourceTxHash = acceptedHash, failureReason = null)
        }
        accountRefresh.notifyChanged()
        return accepted
    }

    private suspend fun unknown(record: PerpsFundingRecord, step: PerpsFundingStep): PerpsFundingRecord {
        return journal.update(record.operationId, record.revision) {
            it.withStep(step.stepId) { current -> current.copy(status = PerpsFundingStepStatus.UNKNOWN) }
                .copy(stage = PerpsDepositStage.SOURCE_UNKNOWN)
        }
    }

    private suspend fun fail(
        record: PerpsFundingRecord,
        step: PerpsFundingStep?,
        reason: String,
        activity: ActivityItem? = null,
    ): PerpsFundingRecord {
        L.e("perps withdraw: ${record.operationId} failed: $reason")
        return journal.update(record.operationId, record.revision) { current ->
            val marked = if (step != null) {
                current.withStep(step.stepId) { it.copy(status = PerpsFundingStepStatus.FAILED) }
            } else {
                current
            }
            marked.copy(
                stage = PerpsDepositStage.FAILED,
                failureReason = reason,
                l1TxHash = activity?.l1TxHashOrNull() ?: current.l1TxHash,
            )
        }
    }

    private fun ActivityItem.l1TxHashOrNull(): String? = txHash?.takeIf { it.isNotBlank() }

    private fun PerpsFundingStep.expiredAtMs(): Long? {
        return try {
            json.parseToJsonElement(signedData).jsonObject[EXPIRED_AT_FIELD]?.jsonPrimitive?.longOrNull
        } catch (e: SerializationException) {
            L.e(e)
            null
        }
    }

    private fun String?.normalizedHash(): String = orEmpty().trim().lowercase().removePrefix("0x")

    private companion object {
        const val TX_TYPE_TRANSFER = 12
        const val TX_TYPE_WITHDRAW = 13
        const val TRACK_INTERVAL_MS = 30_000L
        const val UNKNOWN_EXECUTION_TIMEOUT_MS = 20L * 60L * 1_000L
        const val TRACK_TIMEOUT_MS = 3L * 60L * 60L * 1_000L
        const val EXECUTION_TIME_TOLERANCE_MS = 1_000L
        const val EXPIRY_GRACE_MS = 60_000L
        const val RESEND_GRACE_MS = 30_000L
        const val ACTIVITY_PAGE_SIZE = 20
        const val EXPIRED_AT_FIELD = "ExpiredAt"
        const val NOTHING_SIGNED = "nothing_signed"
        const val HASH_UNKNOWN = "hash_unknown"
        const val EXPIRED = "expired"
        const val EXPIRY_UNKNOWN = "expiry_unknown"
        const val NONCE_CONSUMED = "nonce_consumed"
        const val L2_FAILED = "l2_failed"
        const val L1_UNVERIFIED = "l1_unverified"
        const val EXECUTION_UNKNOWN = "execution_unknown"
        const val SWAP_FAILED = "swap_failed"
        const val SWAP_UNCONFIRMED = "swap_unconfirmed"
        const val NEVER_DELIVERED = "never_delivered"

        // https://github.com/elliottech/lighter-python/blob/a38b6405f362fc14a562fe7a97df03f3ee756bc1/lighter/models/withdraw_history_item.py#L43
        val SETTLED_STATUSES = setOf("claimable", "completed")
        val RETURNED_STATUSES = setOf("failed", "refunded")
        val json = Json { ignoreUnknownKeys = true }
    }
}

