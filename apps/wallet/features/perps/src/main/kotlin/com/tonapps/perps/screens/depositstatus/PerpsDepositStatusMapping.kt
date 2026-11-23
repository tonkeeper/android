package com.tonapps.perps.screens.depositstatus

import com.tonapps.deposit.multicoin.screens.confirm.perpetual.approximateArrivalMinutes
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.formatSignedUsdAmount
import com.tonapps.perps.data.formatTokenCompact
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.data.maskIf
import com.tonapps.perps.funding.PerpsDepositStatus
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.multichain.perps.PerpsDepositPendingStep
import java.math.BigDecimal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

internal fun PerpsDepositBannerState.rowUi(now: Long, hiddenBalance: Boolean): PerpsDepositRowUi? {
    return when (val current = banner) {
        null -> null
        PerpsDepositBanner.Multiple -> PerpsDepositRowUi.Multiple
        PerpsDepositBanner.Problems -> PerpsDepositRowUi.Problems
        is PerpsDepositBanner.Completed -> PerpsDepositRowUi.Completed(
            depositIds = current.depositIds,
            amountFormat = creditedSum(current.depositIds)?.let { sum ->
                maskIf(hiddenBalance) { formatSignedUsdAmount(sum) }
            },
        )

        is PerpsDepositBanner.Single -> {
            val deposit = deposit(current.depositId) ?: return null
            when {
                deposit.state != PerpsDepositStatus.State.PENDING -> PerpsDepositRowUi.Problem(
                    depositId = deposit.id,
                    unconfirmed = deposit.state == PerpsDepositStatus.State.UNCONFIRMED,
                )

                deposit.isDelayed(now) -> PerpsDepositRowUi.Delayed(depositId = deposit.id, step = deposit.step)
                else -> PerpsDepositRowUi.Step(
                    depositId = deposit.id,
                    step = deposit.step,
                    sourceFormat = deposit.sourceFormat(hiddenBalance),
                    etaMinutes = deposit.etaMinutes(),
                    sourceIsUsdc = deposit.sourceIsUsdc,
                )
            }
        }
    }
}

internal fun PerpsDepositBannerState.nextRowChange(now: Long): Long? {
    val single = banner as? PerpsDepositBanner.Single ?: return null
    return deposit(single.depositId)?.delayDeadline(now)
}

private fun PerpsDepositBannerState.creditedSum(depositIds: Set<String>): BigDecimal? {
    val credited = deposits.filter { it.id in depositIds }.map { it.creditedAmount }
    if (credited.size != depositIds.size || credited.any { it == null }) {
        return null
    }
    return credited.filterNotNull().fold(BigDecimal.ZERO, BigDecimal::add)
}

internal fun PerpsDepositStatus.etaMinutes(): Int? = estimatedDurationSeconds?.let(::approximateArrivalMinutes)

internal fun PerpsDepositStatus.sourceFormat(hiddenBalance: Boolean): String {
    return maskIf(hiddenBalance) { formatTokenCompact(sourceAmount, sourceSymbol) }
}

private fun PerpsDepositStatus.received(): BigDecimal? = if (state == PerpsDepositStatus.State.COMPLETED) {
    creditedAmount
} else {
    expectedAmount
}

fun PerpsDepositStatus.toStatusUi(now: Long, hiddenBalance: Boolean): PerpsDepositStatusUi {
    val delayed = isDelayed(now)
    val header = when (state) {
        PerpsDepositStatus.State.COMPLETED -> PerpsDepositStatusHeader.Completed
        PerpsDepositStatus.State.FAILED -> PerpsDepositStatusHeader.Failed
        PerpsDepositStatus.State.REFUNDED -> PerpsDepositStatusHeader.Refunded
        PerpsDepositStatus.State.UNCONFIRMED -> PerpsDepositStatusHeader.Unconfirmed
        PerpsDepositStatus.State.PENDING -> if (delayed) {
            PerpsDepositStatusHeader.Delayed
        } else {
            etaMinutes()?.let { PerpsDepositStatusHeader.Eta(it) }
        }
    }
    val steps = if (step == null && state != PerpsDepositStatus.State.COMPLETED) {
        emptyList()
    } else {
        val kinds = if (skipsConversion) {
            listOf(PerpsDepositStepKind.SENDING, PerpsDepositStepKind.ADDING)
        } else {
            PerpsDepositStepKind.entries
        }
        kinds.map { PerpsDepositStepUi(kind = it, state = stepState(it)) }
    }
    val received = received()
    return PerpsDepositStatusUi(
        depositId = id,
        header = header,
        steps = steps,
        inProgress = state == PerpsDepositStatus.State.PENDING,
        refunded = state == PerpsDepositStatus.State.REFUNDED,
        delayed = delayed,
        sourceFormat = sourceFormat(hiddenBalance),
        sourceSymbol = sourceSymbol,
        sourceIsUsdc = sourceIsUsdc,
        network = sourceNetwork,
        receivedFormat = when {
            hiddenBalance -> HIDDEN_BALANCE
            received == null -> PERPS_EMPTY_VALUE
            else -> formatUsdAmount(received)
        },
        approximate = !hiddenBalance,
    )
}

private fun PerpsDepositStatus.stepState(kind: PerpsDepositStepKind): PerpsDepositStepState {
    if (state == PerpsDepositStatus.State.COMPLETED) {
        return PerpsDepositStepState.DONE
    }
    val current = step?.kind() ?: return PerpsDepositStepState.WAITING
    return when {
        kind.ordinal < current.ordinal -> PerpsDepositStepState.DONE
        kind.ordinal > current.ordinal -> PerpsDepositStepState.WAITING
        state == PerpsDepositStatus.State.PENDING -> PerpsDepositStepState.CURRENT
        else -> PerpsDepositStepState.FAILED
    }
}

internal fun PerpsDepositPendingStep.kind(): PerpsDepositStepKind = when (this) {
    PerpsDepositPendingStep.SENDING -> PerpsDepositStepKind.SENDING
    PerpsDepositPendingStep.CONVERTING -> PerpsDepositStepKind.CONVERTING
    PerpsDepositPendingStep.ADDING -> PerpsDepositStepKind.ADDING
}

internal fun PerpsDepositStatus.toListItemUi(hiddenBalance: Boolean, formatDate: (Long) -> String): PerpsDepositListItemUi {
    val received = received()
    val completed = state == PerpsDepositStatus.State.COMPLETED
    return PerpsDepositListItemUi(
        depositId = id,
        state = when (state) {
            PerpsDepositStatus.State.PENDING -> PerpsDepositListState.DEPOSITING
            PerpsDepositStatus.State.COMPLETED -> PerpsDepositListState.DEPOSITED
            PerpsDepositStatus.State.FAILED,
            PerpsDepositStatus.State.REFUNDED -> PerpsDepositListState.FAILED

            PerpsDepositStatus.State.UNCONFIRMED -> PerpsDepositListState.UNCONFIRMED
        },
        amountFormat = when {
            hiddenBalance -> HIDDEN_BALANCE
            received == null -> PERPS_EMPTY_VALUE
            completed -> formatSignedUsdAmount(received)
            else -> formatUsdAmount(received)
        },
        positive = completed && !hiddenBalance && received != null,
        dateFormat = formatDate(createdAt),
    )
}

fun <T> ticking(next: (Long) -> Long?, produce: (Long) -> T): Flow<T> = flow {
    while (true) {
        val now = System.currentTimeMillis()
        emit(produce(now))
        val at = next(now) ?: break
        delay(at - now)
    }
}

fun PerpsDepositStatus.delayDeadline(now: Long): Long? {
    val estimate = estimatedDurationSeconds?.takeIf { state == PerpsDepositStatus.State.PENDING } ?: return null
    return (createdAt + estimate * MILLIS_IN_SECOND + 1).takeIf { it > now }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<PerpsDepositStatus?>.toStatusUi(hiddenBalance: () -> Boolean): Flow<PerpsDepositStatusUi?> {
    return flatMapLatest { deposit ->
        if (deposit == null) {
            flowOf(null)
        } else {
            ticking(next = { now -> deposit.delayDeadline(now) }) { now -> deposit.toStatusUi(now, hiddenBalance()) }
        }
    }
}

private const val MILLIS_IN_SECOND = 1_000L
