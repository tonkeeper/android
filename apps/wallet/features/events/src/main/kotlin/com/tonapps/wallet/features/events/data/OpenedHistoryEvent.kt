package com.tonapps.wallet.features.events.data

import io.walletapi.models.ActivityType
import io.walletapi.models.Chain
import java.math.BigInteger
import java.time.Duration

private val SETTLEMENT_WINDOW = Duration.ofMinutes(5)

class OpenedHistoryEvent(
    val event: HistoryEventEntity,
    shownHistory: List<HistoryEventEntity>,
) {

    private val earlierTxIds: Set<String> =
        shownHistory.flatMapTo(HashSet()) { it.normalizedTxIds } - event.normalizedTxIds

    fun latestVersionIn(history: List<HistoryEventEntity>): HistoryEventEntity? {
        history.firstOrNull { it == event }?.let { return it }
        val openedKind = event.operationKind
        val sameKind = history.filter { it.operationKind == openedKind }
        val openedTxIds = event.normalizedTxIds
        val sameTransaction = sameKind.filter { activity -> activity.normalizedTxIds.any(openedTxIds::contains) }
        if (sameTransaction.isNotEmpty()) {
            return sameTransaction.singleOrNull()
                ?: sameTransaction.singleOrNull { it.hasSameLegsAs(event) }
        }
        if (!event.isPending) {
            return null
        }
        return sameKind.singleOrNull { it.settles(event) && it.normalizedTxIds.none(earlierTxIds::contains) }
    }
}

private val HistoryEventEntity.operationKind: ActivityType
    get() = if (activityType == ActivityType.bridge) {
        ActivityType.swap
    } else {
        activityType
    }

private val HistoryEventEntity.normalizedTxIds: Set<String>
    get() = txIds.mapNotNullTo(HashSet()) { it.normalizedTxId() }

private fun HistoryEventEntity.hasSameLegsAs(other: HistoryEventEntity): Boolean =
    outAsset?.id == other.outAsset?.id &&
        inAsset?.id == other.inAsset?.id &&
        fromAddress == other.fromAddress &&
        toAddress == other.toAddress

private fun HistoryEventEntity.settles(pending: HistoryEventEntity): Boolean {
    val pendingAsset = pending.outAsset?.id ?: return false
    val pendingAmount = pending.outAmount?.unsignedAmount() ?: return false
    return fromChain == pending.fromChain &&
        outAsset?.id == pendingAsset &&
        outAmount?.unsignedAmount() == pendingAmount &&
        Duration.between(pending.blockTime, blockTime).abs() <= SETTLEMENT_WINDOW
}

private fun String.unsignedAmount(): BigInteger? =
    trim().removePrefix("-").removePrefix("+").toBigIntegerOrNull()

private fun String.normalizedTxId(): String? {
    val value = trim().lowercase()
    val prefix = value.substringBefore(':', missingDelimiterValue = "")
    val isChainPrefix = prefix.isNotEmpty() && Chain.decode(prefix).let { it != null && it != Chain.unknown }
    val hash = if (isChainPrefix) {
        value.substringAfter(':')
    } else {
        value
    }
    return hash.takeIf { it.isNotEmpty() }
}
