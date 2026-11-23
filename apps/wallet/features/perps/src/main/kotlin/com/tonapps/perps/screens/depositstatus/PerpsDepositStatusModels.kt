package com.tonapps.perps.screens.depositstatus

import androidx.compose.runtime.Immutable
import com.tonapps.wallet.data.multichain.perps.PerpsDepositPendingStep

@Immutable
sealed interface PerpsDepositRowUi {

    data class Step(
        val depositId: String,
        val step: PerpsDepositPendingStep?,
        val sourceFormat: String,
        val etaMinutes: Int?,
        val sourceIsUsdc: Boolean,
    ) : PerpsDepositRowUi

    data class Delayed(
        val depositId: String,
        val step: PerpsDepositPendingStep?,
    ) : PerpsDepositRowUi

    data class Problem(
        val depositId: String,
        val unconfirmed: Boolean,
    ) : PerpsDepositRowUi

    data object Multiple : PerpsDepositRowUi

    data object Problems : PerpsDepositRowUi

    data class Completed(
        val depositIds: Set<String>,
        val amountFormat: String?,
    ) : PerpsDepositRowUi
}

enum class PerpsDepositToast {
    IN_PROGRESS,
    COMPLETED,
}

enum class PerpsDepositStepKind {
    SENDING,
    CONVERTING,
    ADDING,
}

enum class PerpsDepositStepState {
    WAITING,
    CURRENT,
    DONE,
    FAILED,
}

@Immutable
sealed interface PerpsDepositStatusHeader {
    data class Eta(val minutes: Int) : PerpsDepositStatusHeader
    data object Delayed : PerpsDepositStatusHeader
    data object Completed : PerpsDepositStatusHeader
    data object Failed : PerpsDepositStatusHeader
    data object Refunded : PerpsDepositStatusHeader
    data object Unconfirmed : PerpsDepositStatusHeader
}

@Immutable
data class PerpsDepositStepUi(
    val kind: PerpsDepositStepKind,
    val state: PerpsDepositStepState,
)

@Immutable
data class PerpsDepositStatusUi(
    val depositId: String,
    val header: PerpsDepositStatusHeader?,
    val steps: List<PerpsDepositStepUi>,
    val inProgress: Boolean,
    val refunded: Boolean,
    val delayed: Boolean,
    val sourceFormat: String,
    val sourceSymbol: String,
    val sourceIsUsdc: Boolean,
    val network: String,
    val receivedFormat: String,
    val approximate: Boolean,
)

@Immutable
data class PerpsDepositListItemUi(
    val depositId: String,
    val state: PerpsDepositListState,
    val amountFormat: String,
    val positive: Boolean,
    val dateFormat: String,
)

enum class PerpsDepositListState {
    DEPOSITING,
    DEPOSITED,
    FAILED,
    UNCONFIRMED,
}
