package com.tonapps.wallet.features.events.screens.details

import com.tonapps.perps.data.formatSignedPercent
import com.tonapps.perps.data.formatUsd
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.features.events.data.HistoryEventEntity
import com.tonapps.wallet.features.events.data.PerpsCloseReason
import com.tonapps.wallet.features.events.data.PerpsMetaEntity
import com.tonapps.wallet.features.events.data.PerpsSide
import com.tonapps.wallet.localization.Localization
import io.walletapi.models.ActivityDirection
import io.walletapi.models.ActivityStatus
import io.walletapi.models.ActivityType
import java.math.BigDecimal
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class PerpsPositionDetailsRowsTest {

    @Test
    fun aClosedPositionKeepsItsResultPercentWhileBalancesAreHidden() {
        val rows = closed(resultPct = BigDecimal("-3.07")).perpsDetailsRows(hiddenBalance = true)

        assertEquals(formatSignedPercent(BigDecimal("-3.07")), rows.valueOf(Localization.perps_loss))
        assertEquals(HIDDEN_BALANCE, rows.valueOf(Localization.perps_size))
        assertEquals(HIDDEN_BALANCE, rows.valueOf(Localization.fee))
        assertEquals(HIDDEN_BALANCE, rows.valueOf(Localization.perps_funding))
        assertEquals(formatUsd(BigDecimal("2287.81")), rows.valueOf(Localization.perps_entry_price))
        assertEquals(formatUsd(BigDecimal("2286.47")), rows.valueOf(Localization.perps_close_price))
    }

    @Test
    fun aPartialCloseKeepsItsResultPercentWhileBalancesAreHidden() {
        val rows = closed(
            type = ActivityType.perpsPeriodPosition_reduced,
            resultPct = BigDecimal("40.18"),
        ).perpsDetailsRows(hiddenBalance = true)

        assertEquals(formatSignedPercent(BigDecimal("40.18")), rows.valueOf(Localization.perps_profit))
        assertEquals(HIDDEN_BALANCE, rows.valueOf(Localization.perps_size))
    }

    @Test
    fun theResultPercentReadsTheSameWhetherBalancesAreHiddenOrNot() {
        val event = closed(resultPct = BigDecimal("-3.07"))

        assertEquals(
            event.perpsDetailsRows(hiddenBalance = false).valueOf(Localization.perps_loss),
            event.perpsDetailsRows(hiddenBalance = true).valueOf(Localization.perps_loss),
        )
    }

    private fun List<PerpsDetailsRow>.valueOf(titleRes: Int): String? =
        singleOrNull { it.titleRes == titleRes }?.value

    private fun closed(
        type: ActivityType = ActivityType.perpsPeriodPosition_closed,
        resultPct: BigDecimal,
    ) = HistoryEventEntity(
        activityType = type,
        status = ActivityStatus.confirmed,
        blockTime = OffsetDateTime.parse("2026-11-02T11:36:00Z"),
        walletAddress = null,
        fromAddress = null,
        toAddress = null,
        fromChain = null,
        toChain = null,
        inAmount = null,
        outAmount = "29800",
        inAmountUsd = null,
        outAmountUsd = null,
        feeAmount = "18400",
        feeAmountUsd = null,
        inAsset = null,
        outAsset = LIGHTER_USDC,
        feeAsset = LIGHTER_USDC,
        txIds = emptyList(),
        blockNumber = null,
        protocol = "lighter",
        explorerUrl = null,
        comment = null,
        direction = ActivityDirection.self,
        perps = PerpsMetaEntity(
            symbol = "ETH",
            side = PerpsSide.Long,
            reason = PerpsCloseReason.Manual,
            marketIndex = 0,
            leverage = 20,
            entryPrice = BigDecimal("2287.81"),
            closePrice = BigDecimal("2286.47"),
            size = BigDecimal("0.0085"),
            sizeUsd = BigDecimal("19.43"),
            resultPct = resultPct,
            fundingUsd = BigDecimal.ZERO,
        ),
    )

    private companion object {
        val LIGHTER_USDC = AssetEntity(
            id = "lighter/mainnet/3",
            name = "USDC",
            symbol = "USDC",
            decimals = 6,
            imageUrl = "",
        )
    }
}
