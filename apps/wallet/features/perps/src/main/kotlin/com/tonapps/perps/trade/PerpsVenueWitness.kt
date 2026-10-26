package com.tonapps.perps.trade

import com.tonapps.chainkit.lighter.LighterConstants
import com.tonapps.mvi.graph.KResult
import com.tonapps.wallet.data.multichain.perps.PerpsOperationKind
import com.tonapps.wallet.data.multichain.perps.PerpsOperationRecord
import com.tonapps.wallet.data.multichain.perps.PerpsStepKind
import com.tonapps.wallet.data.multichain.perps.PerpsStepRecord
import com.tonapps.perps.data.PerpsRepository
import kotlinx.coroutines.delay
import java.math.BigDecimal

internal class PerpsVenueWitness(
    private val repository: PerpsRepository,
) {

    suspend fun openedOnVenue(
        walletId: String,
        marketIndex: Int,
        side: String,
        expectedBase: BigDecimal?,
    ): Boolean? {
        val result = repository.getOpenPosition(marketIndex, walletId) as? KResult.Ok ?: return null
        val position = result.value?.position ?: return false
        if (position.side.name != side) {
            return false
        }
        if (expectedBase == null) {
            return true
        }

        return position.size?.coversBase(expectedBase) == true
    }

    suspend fun legsOnVenue(walletId: String, marketIndex: Int): Set<Long>? {
        val result = repository.getOpenPosition(marketIndex, walletId) as? KResult.Ok ?: return null
        val detail = result.value ?: return emptySet()

        return setOfNotNull(detail.takeProfit?.clientOrderIndex, detail.stopLoss?.clientOrderIndex)
    }

    suspend fun cancelledOnVenue(walletId: String, marketIndex: Int, orderIndex: Long): Boolean? {
        val result = repository.getOpenPosition(marketIndex, walletId) as? KResult.Ok ?: return null
        val detail = result.value ?: return true
        if (!detail.autoCloseKnown) {
            return null
        }

        return listOfNotNull(detail.takeProfit, detail.stopLoss)
            .none { it.orderIndex == orderIndex || it.clientOrderIndex == orderIndex }
    }

    suspend fun restingOnVenue(walletId: String, marketIndex: Int, clientOrderIndex: Long): Boolean? {
        val result = repository.getOpenOrders(marketIndex, walletId) as? KResult.Ok ?: return null

        return result.value.any { it.clientOrderIndex == clientOrderIndex }
    }

    suspend fun limitOnVenue(walletId: String, marketIndex: Int, orderIndex: Long, price: BigDecimal?): PerpsLimitOnVenue? {
        val result = repository.getOpenOrders(marketIndex, walletId) as? KResult.Ok ?: return null
        val resting = result.value.firstOrNull { it.orderIndex == orderIndex } ?: return PerpsLimitOnVenue.GONE

        return if (price != null && resting.limitPrice?.compareTo(price) == 0) {
            PerpsLimitOnVenue.REPRICED
        } else {
            PerpsLimitOnVenue.RESTING
        }
    }

    suspend fun cancelOutcome(operation: PerpsOperationRecord, step: PerpsStepRecord): PerpsCancelOnVenue? {
        when (limitOnVenue(operation.walletId, operation.marketIndex, step.clientOrderIndex, price = null)) {
            PerpsLimitOnVenue.GONE -> Unit
            null -> return null
            else -> return PerpsCancelOnVenue.RESTING
        }
        val changed = positionChanged(operation) ?: return null
        if (changed) {
            return PerpsCancelOnVenue.FILLED
        }
        delay(POSITION_RECHECK_MS)

        return when (positionChanged(operation)) {
            false -> PerpsCancelOnVenue.CANCELLED
            true -> PerpsCancelOnVenue.FILLED
            null -> null
        }
    }

    private suspend fun positionChanged(operation: PerpsOperationRecord): Boolean? {
        val result = repository.getOpenPosition(operation.marketIndex, operation.walletId) as? KResult.Ok ?: return null
        val position = result.value?.position
        val before = operation.baseBefore?.toBigDecimalOrNull()
        val size = position?.size?.abs()

        return when {
            position == null -> before != null
            before == null -> true
            position.side.name != operation.side -> true
            else -> size != null &&
                size.subtract(before).abs() > before.multiply(FILL_TOLERANCE_RATIO).max(MIN_FILL_TOLERANCE)
        }
    }

    private suspend fun limitOpenedOnVenue(operation: PerpsOperationRecord, step: PerpsStepRecord): Boolean? {
        val resting = restingOnVenue(operation.walletId, operation.marketIndex, step.clientOrderIndex)
        if (resting == true) {
            return true
        }
        val result = repository.getOpenPosition(operation.marketIndex, operation.walletId) as? KResult.Ok ?: return null
        val position = result.value?.position
        val before = operation.baseBefore?.toBigDecimalOrNull()
        val filled = position != null && position.side.name == operation.side &&
            (before == null || position.size?.abs()?.let { it > before } == true)

        return true.takeIf { filled } ?: resting
    }

    suspend fun closedOnVenue(walletId: String, marketIndex: Int): Boolean? {
        val result = repository.getOpenPosition(marketIndex, walletId) as? KResult.Ok ?: return null

        return result.value == null
    }

    suspend fun sizeOnVenue(walletId: String, marketIndex: Int, expected: BigDecimal, grew: Boolean): Boolean? {
        val result = repository.getOpenPosition(marketIndex, walletId) as? KResult.Ok ?: return null
        val size = result.value?.position?.size?.abs() ?: return false
        val tolerance = expected.abs().multiply(FILL_TOLERANCE_RATIO).max(MIN_FILL_TOLERANCE)

        return if (grew) {
            size.add(tolerance) >= expected
        } else {
            size.subtract(tolerance) <= expected
        }
    }

    suspend fun movedOnVenue(operation: PerpsOperationRecord, step: PerpsStepRecord): Boolean? {
        val before = operation.baseBefore?.toBigDecimalOrNull()
        val grew = when {
            step.kind == PerpsStepKind.OPEN && operation.kind == PerpsOperationKind.OPEN -> true
            step.kind == PerpsStepKind.ADD && before != null -> true
            step.kind == PerpsStepKind.CLOSE && before != null -> false
            else -> return null
        }
        val result = repository.getOpenPosition(operation.marketIndex, operation.walletId) as? KResult.Ok ?: return null
        val position = result.value?.position
        val size = position?.size?.abs()
        val start = before ?: BigDecimal.ZERO
        val tolerance = start.abs().multiply(FILL_TOLERANCE_RATIO).max(MIN_FILL_TOLERANCE)

        return if (grew) {
            position?.side?.name == operation.side && size != null && size > start.add(tolerance)
        } else {
            position == null || (size != null && size < start.subtract(tolerance))
        }
    }

    fun of(operation: PerpsOperationRecord, step: PerpsStepRecord): (suspend () -> Boolean?)? {
        val walletId = operation.walletId
        val marketIndex = operation.marketIndex

        return when (step.kind) {
            PerpsStepKind.OPEN -> if (operation.kind == PerpsOperationKind.OPEN_LIMIT) {
                suspend { limitOpenedOnVenue(operation, step) }
            } else {
                suspend { openedOnVenue(walletId, marketIndex, operation.side, operation.expectedBase()) }
            }

            PerpsStepKind.ADD -> operation.baseAfter(grew = true)
                ?.let { suspend { sizeOnVenue(walletId, marketIndex, it, grew = true) } }

            PerpsStepKind.CLOSE -> if (operation.kind == PerpsOperationKind.CLOSE) {
                suspend { closedOnVenue(walletId, marketIndex) }
            } else {
                operation.baseAfter(grew = false)
                    ?.let { suspend { sizeOnVenue(walletId, marketIndex, it, grew = false) } }
            }

            PerpsStepKind.AUTO_CLOSE -> suspend {
                legsOnVenue(walletId, marketIndex)
                    ?.containsAll(step.legIndexes().filter { it > LighterConstants.NilClientOrderIndex })
            }

            PerpsStepKind.ORDER_CANCEL -> if (operation.kind == PerpsOperationKind.ORDER_CANCEL) {
                suspend {
                    when (cancelOutcome(operation, step)) {
                        PerpsCancelOnVenue.CANCELLED -> true
                        PerpsCancelOnVenue.RESTING -> false
                        PerpsCancelOnVenue.FILLED, null -> null
                    }
                }
            } else {
                suspend { cancelledOnVenue(walletId, marketIndex, step.clientOrderIndex) }
            }

            PerpsStepKind.ORDER_MODIFY -> operation.marginQuote.toBigDecimalOrNull()?.let {
                suspend {
                    when (limitOnVenue(walletId, marketIndex, step.clientOrderIndex, it)) {
                        PerpsLimitOnVenue.REPRICED -> true
                        null -> null
                        else -> false
                    }
                }
            }

            else -> null
        }
    }
}

internal enum class PerpsLimitOnVenue {
    RESTING,
    REPRICED,
    GONE,
}

internal enum class PerpsCancelOnVenue {
    CANCELLED,
    FILLED,
    RESTING,
}

private const val POSITION_RECHECK_MS = 3_000L
