package com.tonapps.perps.funding

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.log.L
import com.tonapps.perps.data.PERPS_SLOW_POLL_MS
import com.tonapps.perps.data.PerpsAccountRefresh
import com.tonapps.perps.data.PerpsRealtime
import com.tonapps.perps.data.PerpsRealtimeEvent
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.perps.PerpsDepositRoute
import com.tonapps.wallet.data.multichain.perps.PerpsDepositStage
import com.tonapps.wallet.data.multichain.perps.PerpsFundingJournal
import com.tonapps.wallet.data.multichain.perps.PerpsFundingKind
import com.tonapps.wallet.data.multichain.perps.PerpsFundingRecord
import com.tonapps.wallet.data.multichain.perps.PerpsFundingStep
import com.tonapps.wallet.data.multichain.perps.PerpsFundingStepStatus
import io.perpsapi.models.FundingStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class PerpsFundingRecovery(
    private val journal: PerpsFundingJournal,
    private val registrar: PerpsDepositRegistrar,
    private val repository: PerpsRepository,
    private val transactionLookup: PerpsTransactionLookup,
    private val accountRefresh: PerpsAccountRefresh,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val withdrawTracker: PerpsWithdrawTracker,
    private val realtime: PerpsRealtime,
    private val scope: CoroutineScope,
) {

    private val loops = ConcurrentHashMap<String, Job>()
    private val held = ConcurrentHashMap.newKeySet<String>()

    fun start() {
        scope.launch {
            unifiedAccountRepository.selectedTonWalletFlow
                .map { wallet -> wallet?.takeIf { it.supportsPerps }?.id }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { walletId -> refresh(walletId) }
        }
    }

    fun observe(walletId: String): Flow<List<PerpsFundingRecord>> = journal.observe(walletId)

    fun observeNewOutcomes(walletId: String): Flow<Unit> = flow {
        var reached: Set<String>? = null
        journal.observe(walletId).collect { records ->
            val outcomes = records.filter { it.stage.isOutcome }.mapTo(HashSet()) { it.operationId }
            val previous = reached
            reached = outcomes
            if (previous != null && !previous.containsAll(outcomes)) {
                emit(Unit)
            }
        }
    }

    fun refresh(walletId: String) {
        loops.compute(walletId) { _, existing ->
            existing?.cancel()
            scope.launch { loop(walletId) }
        }
    }

    suspend fun <T> holding(operationId: String, block: suspend () -> T): T {
        held += operationId
        try {
            return block()
        } finally {
            held -= operationId
        }
    }

    private suspend fun loop(walletId: String) = coroutineScope {
        val perpsEvents = realtime.wakeups(
            walletId = walletId,
            unsubscribedPeriodMs = PERPS_SLOW_POLL_MS,
            subscribedTimeoutMs = PERPS_SLOW_POLL_MS,
            wakeOn = { it is PerpsRealtimeEvent.Deposit || it is PerpsRealtimeEvent.Withdrawal },
        ).produceIn(this)
        try {
            var failures = 0
            while (true) {
                val pass = try {
                    pass(walletId)
                } catch (e: Throwable) {
                    if (e is CancellationException) {
                        throw e
                    }
                    L.e(e)
                    Pass(shouldPoll = true, failed = true)
                }
                if (!pass.shouldPoll) {
                    break
                }
                if (pass.failed) {
                    failures = (failures + 1).coerceAtMost(MAX_BACKOFF_SHIFT)
                    delay(BASE_DELAY_MS shl failures)
                } else {
                    failures = 0
                    when {
                        pass.backgroundOnly -> delay(BACKGROUND_POLL_DELAY_MS)
                        pass.awaitsPerpsEvents -> perpsEvents.receive()
                        else -> delay(POLL_DELAY_MS)
                    }
                }
            }
        } finally {
            perpsEvents.cancel()
        }
    }

    private suspend fun pass(walletId: String): Pass {
        journal.cleanup()
        var shouldPoll = false
        var failed = false
        var foreground = false
        var timerDriven = false
        for (record in journal.list(walletId).filter { it.stage.isUnresolved && it.operationId !in held }) {
            try {
                val updated = advance(record) ?: continue
                val polls = updated.stage.isUnresolved && updated.stage != PerpsDepositStage.NEEDS_AUTHORIZATION
                val pollsInForeground = polls && !updated.stage.isTrackingInBackground
                shouldPoll = shouldPoll || polls
                foreground = foreground || pollsInForeground
                timerDriven = timerDriven || pollsInForeground && !updated.awaitsPerpsEvent()
                if (updated.stage.isSucceeded && !record.stage.isSucceeded) {
                    accountRefresh.notifyChanged()
                }
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                L.e(e, "perps funding: ${record.operationId} at ${record.stage} did not advance")
                shouldPoll = true
                failed = true
                foreground = true
                timerDriven = true
            }
        }
        return Pass(
            shouldPoll = shouldPoll,
            failed = failed,
            backgroundOnly = shouldPoll && !foreground,
            awaitsPerpsEvents = foreground && !timerDriven,
        )
    }

    private fun PerpsFundingRecord.awaitsPerpsEvent(): Boolean = when (kind) {
        PerpsFundingKind.WITHDRAW -> stage == PerpsDepositStage.SOURCE_SENDING || stage == PerpsDepositStage.SOURCE_UNKNOWN
        PerpsFundingKind.DEPOSIT -> stage == PerpsDepositStage.REGISTERED_PENDING
    }

    private suspend fun advance(record: PerpsFundingRecord): PerpsFundingRecord? {
        if (!record.stepsReadable) {
            return null
        }
        if (record.kind == PerpsFundingKind.WITHDRAW) {
            return withdrawTracker.advance(record)
        }
        return when (record.stage) {
            PerpsDepositStage.SOURCE_SIGNED,
            PerpsDepositStage.SOURCE_SENDING,
            PerpsDepositStage.SOURCE_UNKNOWN -> settleAmbiguous(record)
            PerpsDepositStage.AWAITING_DELIVERY -> trackDelivery(record)
            PerpsDepositStage.REGISTRATION_PENDING -> registrar.register(record)
            PerpsDepositStage.REGISTERED_PENDING -> registrar.refresh(record)
            PerpsDepositStage.PREPARING,
            PerpsDepositStage.NEEDS_AUTHORIZATION,
            PerpsDepositStage.CREDITED,
            PerpsDepositStage.UNCONFIRMED,
            PerpsDepositStage.FAILED,
            PerpsDepositStage.REFUNDED,
            PerpsDepositStage.ACCEPTED,
            PerpsDepositStage.AWAITING_L1,
            PerpsDepositStage.SETTLED -> null
        }
    }

    private suspend fun resend(record: PerpsFundingRecord, step: PerpsFundingStep): PerpsFundingRecord {
        return journal.update(record.operationId, record.revision) {
            it.settled(step.stepId, PerpsStepOutcome.Failed(NOT_SENT))
        }
    }

    private suspend fun settleAmbiguous(record: PerpsFundingRecord): PerpsFundingRecord {
        val step = record.steps.firstOrNull { it.isUnresolved }
            ?: return journal.update(record.operationId, record.revision) {
                it.copy(stage = PerpsDepositStage.FAILED, failureReason = NOTHING_SIGNED)
            }
        if (step.status == PerpsFundingStepStatus.SIGNED) {
            return resend(record, step)
        }
        val hash = step.txHash?.takeIf { transactionLookup.canLookUp(chainOf(step.chainId)) }
            ?: return journal.update(record.operationId, record.revision) { current ->
                if (current.route == PerpsDepositRoute.CROSSCHAIN_SWAP) {
                    current.copy(stage = PerpsDepositStage.REGISTRATION_PENDING)
                } else {
                    current.copy(stage = PerpsDepositStage.FAILED, failureReason = HASH_UNKNOWN)
                }
            }
        return when (chainVerdict(record, step, hash)) {
            ChainVerdict.WAIT -> record
            ChainVerdict.MISSING -> resend(record, step)
            ChainVerdict.REVERTED -> journal.update(record.operationId, record.revision) {
                it.settled(step.stepId, PerpsStepOutcome.Failed(TX_REVERTED))
            }

            ChainVerdict.SENT -> journal.update(record.operationId, record.revision) {
                it.settled(step.stepId, PerpsStepOutcome.Sent(hash))
            }
        }
    }

    private suspend fun chainVerdict(record: PerpsFundingRecord, step: PerpsFundingStep, hash: String): ChainVerdict {
        val age = System.currentTimeMillis() - record.updatedAt
        return when (transactionLookup.presence(record.walletId, record.sourceAssetId, chainOf(step.chainId), hash)) {
            PerpsTransactionPresence.KNOWN -> ChainVerdict.SENT
            PerpsTransactionPresence.REVERTED -> ChainVerdict.REVERTED
            PerpsTransactionPresence.UNKNOWN -> if (age > NOT_FOUND_GRACE_MS) {
                ChainVerdict.MISSING
            } else {
                ChainVerdict.WAIT
            }

            PerpsTransactionPresence.UNAVAILABLE -> if (age > LOOKUP_TIMEOUT_MS) {
                ChainVerdict.SENT
            } else {
                ChainVerdict.WAIT
            }
        }
    }

    private fun chainOf(chainId: String): Chain {
        val (label, net) = chainId.split('/')
        return Chain.find(label, net)
            ?: throw IllegalStateException("perps funding: unknown chain $chainId")
    }

    private enum class ChainVerdict {
        SENT,
        REVERTED,
        MISSING,
        WAIT,
    }

    private suspend fun trackDelivery(record: PerpsFundingRecord): PerpsFundingRecord {
        val executionId = record.executionId
            ?: throw IllegalStateException("perps funding: ${record.operationId} has no execution id")
        val status = repository.getFundingStatus(record.walletId, executionId)
        val now = System.currentTimeMillis()
        return journal.update(record.operationId, record.revision) { current ->
            when (status.status) {
                FundingStatus.Status.success -> {
                    val delivered = status.txHashes?.firstOrNull()?.trim()?.lowercase()
                    if (delivered.isNullOrEmpty()) {
                        current
                    } else {
                        current.copy(l1TxHash = delivered, stage = PerpsDepositStage.REGISTRATION_PENDING)
                    }
                }

                FundingStatus.Status.failure -> current.copy(
                    stage = PerpsDepositStage.FAILED,
                    failureReason = status.details ?: DELIVERY_FAILED,
                )

                FundingStatus.Status.refund -> current.copy(
                    stage = PerpsDepositStage.REFUNDED,
                    failureReason = status.details,
                )

                FundingStatus.Status.unknown -> if (now - current.createdAt > UNKNOWN_EXECUTION_TIMEOUT_MS) {
                    current.copy(stage = PerpsDepositStage.FAILED, failureReason = NEVER_DELIVERED)
                } else {
                    current
                }

                FundingStatus.Status.pending -> current
            }
        }
    }

    private class Pass(
        val shouldPoll: Boolean,
        val failed: Boolean,
        val backgroundOnly: Boolean = false,
        val awaitsPerpsEvents: Boolean = false,
    )

    private companion object {
        const val POLL_DELAY_MS = 5_000L
        const val BACKGROUND_POLL_DELAY_MS = 30_000L
        const val BASE_DELAY_MS = 2_000L
        const val MAX_BACKOFF_SHIFT = 5
        const val UNKNOWN_EXECUTION_TIMEOUT_MS = 20L * 60L * 1_000L
        const val NOT_FOUND_GRACE_MS = 3L * 60L * 1_000L
        const val LOOKUP_TIMEOUT_MS = 15L * 60L * 1_000L
        const val NOTHING_SIGNED = "nothing_signed"
        const val HASH_UNKNOWN = "hash_unknown"
        const val NOT_SENT = "not_sent"
        const val DELIVERY_FAILED = "delivery_failed"
        const val NEVER_DELIVERED = "never_delivered"
    }
}
