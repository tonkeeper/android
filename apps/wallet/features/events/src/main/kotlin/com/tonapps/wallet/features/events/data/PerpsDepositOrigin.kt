package com.tonapps.wallet.features.events.data

import androidx.compose.runtime.Immutable
import com.tonapps.perps.funding.PerpsFundingAmount
import com.tonapps.perps.funding.perpsTransactionHash
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import java.math.BigDecimal
import kotlin.math.abs

@Immutable
data class PerpsDepositOrigin(
    val asset: AssetEntity,
    val amount: BigDecimal,
)

internal class PerpsDepositOriginRecord(
    val transactions: Set<String>,
    val expectedCredit: BigDecimal?,
    val viaAggregator: Boolean,
    val createdAt: Long,
    val origin: PerpsDepositOrigin,
)

internal fun List<PerpsDepositOriginRecord>.originOf(event: HistoryEventEntity): PerpsDepositOrigin? {
    if (event.perpsKind != PerpsEventKind.Deposit) {
        return null
    }
    val hashes = event.txIds.mapNotNullTo(HashSet()) { perpsTransactionHash(it) }
    firstOrNull { record -> record.transactions.any(hashes::contains) }?.let { return it.origin }
    val credited = event.depositedAmount() ?: return null
    val creditedAt = event.blockTime.toInstant().toEpochMilli()
    return filter { record ->
        record.viaAggregator &&
            record.expectedCredit?.isCloseTo(credited) == true &&
            creditedAt >= record.createdAt - CLOCK_SKEW_MS &&
            creditedAt <= record.createdAt + CREDIT_WINDOW_MS
    }
        .minByOrNull { abs(creditedAt - it.createdAt) }
        ?.origin
}

private fun HistoryEventEntity.depositedAmount(): BigDecimal? {
    val units = inAmount?.toBigIntegerOrNull() ?: return null
    val decimals = inAsset?.decimals ?: return null
    return PerpsFundingAmount.toDecimal(units, decimals)
}

private fun BigDecimal.isCloseTo(credited: BigDecimal): Boolean {
    return subtract(credited).abs() <= multiply(AMOUNT_TOLERANCE)
}

private val AMOUNT_TOLERANCE = BigDecimal("0.02")
private const val CLOCK_SKEW_MS = 5 * 60 * 1000L
private const val CREDIT_WINDOW_MS = 60 * 60 * 1000L
