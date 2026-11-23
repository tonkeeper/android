package com.tonapps.perps.screens.depositstatus

import com.tonapps.perps.funding.PerpsDepositStatus

sealed interface PerpsDepositBanner {
    data class Single(val depositId: String) : PerpsDepositBanner
    data object Multiple : PerpsDepositBanner
    data object Problems : PerpsDepositBanner
    data class Completed(val depositIds: Set<String>) : PerpsDepositBanner
}

class PerpsDepositBannerState(
    var isVisible: Boolean = true,
) {

    var deposits: List<PerpsDepositStatus> = emptyList()
        private set

    var banner: PerpsDepositBanner? = null
        private set

    val listedDeposits: List<PerpsDepositStatus>
        get() = deposits.filter { it.state == PerpsDepositStatus.State.PENDING || it.id !in hiddenFromList }

    private var watched: Set<String> = emptySet()
    private val acknowledged = HashSet<String>()
    private val grouped = HashSet<String>()
    private var hiddenFromList: Set<String> = emptySet()

    fun apply(values: List<PerpsDepositStatus>, isBalanceRefreshing: Boolean = false): Set<String> {
        values.filter { it.acknowledged }.mapTo(acknowledged) { it.id }
        val next = values
            .map { value ->
                if (isBalanceRefreshing && value.state == PerpsDepositStatus.State.COMPLETED && value.id in watched) {
                    deposits.firstOrNull { it.id == value.id } ?: value
                } else {
                    value
                }
            }
            .sortedWith(compareByDescending<PerpsDepositStatus> { it.createdAt }.thenByDescending { it.id })
        val pending = next.filter { it.state == PerpsDepositStatus.State.PENDING }.mapTo(HashSet()) { it.id }
        val completed = next.filter { it.state == PerpsDepositStatus.State.COMPLETED }.mapTo(HashSet()) { it.id }
        val arrived = watched intersect completed
        watched = pending
        deposits = next
        val current = banner
        banner = when {
            pending.size > 1 -> {
                grouped += pending
                PerpsDepositBanner.Multiple
            }

            pending.size == 1 -> PerpsDepositBanner.Single(pending.first())
            arrived.isNotEmpty() && isVisible -> PerpsDepositBanner.Completed(arrived)
            current is PerpsDepositBanner.Completed -> current
            else -> {
                val problems = next.filter { it.isProblem && it.id !in acknowledged }
                if (problems.any { it.id in grouped }) {
                    PerpsDepositBanner.Problems
                } else {
                    problems.firstOrNull()?.let { PerpsDepositBanner.Single(it.id) }
                }
            }
        }
        return arrived
    }

    fun didInteract() {
        if (banner is PerpsDepositBanner.Completed) {
            banner = null
            apply(deposits)
        }
    }

    fun didOpenList(announced: Set<String> = emptySet()): Set<String> {
        hiddenFromList = deposits
            .filter { it.state != PerpsDepositStatus.State.PENDING && it.id in acknowledged && it.id !in announced }
            .mapTo(HashSet()) { it.id }
        val problems = deposits.filter { it.isProblem }.mapTo(HashSet()) { it.id }
        acknowledged += problems
        apply(deposits)
        return problems
    }

    fun didCloseList(): Set<String> {
        val settled = deposits
            .filter { it.state != PerpsDepositStatus.State.PENDING && it.id !in acknowledged }
            .mapTo(HashSet()) { it.id }
        acknowledged += settled
        apply(deposits)
        return settled
    }

    fun didOpen(depositId: String): Set<String> {
        val deposit = deposit(depositId)?.takeIf { it.isProblem } ?: return emptySet()
        acknowledged += deposit.id
        apply(deposits)
        return setOf(deposit.id)
    }

    fun deposit(depositId: String): PerpsDepositStatus? = deposits.firstOrNull { it.id == depositId }
}
