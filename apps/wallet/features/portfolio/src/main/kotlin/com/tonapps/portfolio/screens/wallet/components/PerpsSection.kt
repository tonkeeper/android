package com.tonapps.portfolio.screens.wallet.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.PerpsPosition
import com.tonapps.perps.data.PerpsPositionSide
import com.tonapps.perps.screens.portfolio.PerpsPositionUi
import com.tonapps.perps.screens.portfolio.PerpsPositionsCard
import com.tonapps.perps.screens.portfolio.PerpsShelfRow
import com.tonapps.perps.screens.portfolio.toUi
import com.tonapps.portfolio.screens.wallet.WalletSection
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.localization.Localization
import androidx.compose.ui.tooling.preview.Preview
import ui.components.moon.cell.MoonBundleTitleCell
import ui.preview.ThemedPreview
import java.math.BigDecimal

@Composable
internal fun PerpsPositionsSection(
    state: WalletSection.Perps.Positions,
    onOpenPerps: () -> Unit,
    onOpenPerpMarket: (marketIndex: Int, symbol: String) -> Unit,
) {
    val balanceFormat = state.balanceFormat
    Column {
        MoonBundleTitleCell(
            title = stringResource(Localization.perps_title),
            onClick = onOpenPerps,
            content = if (balanceFormat != null) {
                { SectionBalance(format = balanceFormat, onClick = onOpenPerps) }
            } else {
                null
            },
        )
        PerpsPositionsCard(positions = state.positions, onOpenMarket = onOpenPerpMarket)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
internal fun PerpsShelfSection(
    state: WalletSection.Perps.Shelf,
    onOpenPerps: () -> Unit,
    onOpenPerpMarket: (marketIndex: Int, symbol: String) -> Unit,
) {
    val balanceFormat = state.balanceFormat
    Column {
        MoonBundleTitleCell(
            title = stringResource(Localization.perps_title),
            onClick = onOpenPerps,
            content = if (balanceFormat != null) {
                { SectionBalance(format = balanceFormat, onClick = onOpenPerps) }
            } else {
                null
            },
        )
        PerpsShelfRow(markets = state.markets, onOpenPerpMarket = onOpenPerpMarket)
    }
}

@Preview
@Composable
private fun PerpsPositionsSectionPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsPositionsSection(
            state = WalletSection.Perps.Positions(
                positions = listOf(
                    previewPosition(marketIndex = 1, symbol = "BTC", side = PerpsPositionSide.LONG, pnl = "20.50", equity = "40.50", hiddenBalance = false),
                    previewPosition(marketIndex = 2, symbol = "ETH", side = PerpsPositionSide.SHORT, pnl = "-4.20", equity = "7.80", hiddenBalance = false),
                ),
                balanceFormat = "$48.30",
            ),
            onOpenPerps = {},
            onOpenPerpMarket = { _, _ -> },
        )
    }
}

@Preview
@Composable
private fun PerpsPositionsSectionHiddenPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsPositionsSection(
            state = WalletSection.Perps.Positions(
                positions = listOf(
                    previewPosition(marketIndex = 1, symbol = "BTC", side = PerpsPositionSide.LONG, pnl = "20.50", equity = "40.50", hiddenBalance = true),
                    previewPosition(marketIndex = 2, symbol = "ETH", side = PerpsPositionSide.SHORT, pnl = "-4.20", equity = "7.80", hiddenBalance = true),
                ),
                balanceFormat = HIDDEN_BALANCE,
            ),
            onOpenPerps = {},
            onOpenPerpMarket = { _, _ -> },
        )
    }
}

@Preview
@Composable
private fun PerpsShelfSectionPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsShelfSection(
            state = WalletSection.Perps.Shelf(
                markets = listOf(
                    previewMarket(marketIndex = 0, symbol = "BTC", maxLeverage = 40, change = "10.23"),
                    previewMarket(marketIndex = 1, symbol = "ETH", maxLeverage = 25, change = "3.91"),
                    previewMarket(marketIndex = 2, symbol = "TSLAx", maxLeverage = 10, change = "-1.21"),
                ),
                balanceFormat = "$ 2 712.12",
            ),
            onOpenPerps = {},
            onOpenPerpMarket = { _, _ -> },
        )
    }
}

private fun previewMarket(
    marketIndex: Int,
    symbol: String,
    maxLeverage: Int,
    change: String,
): PerpMarket = PerpMarket(
    marketIndex = marketIndex,
    symbol = symbol,
    maxLeverage = maxLeverage,
    price = null,
    priceChange24hPercent = BigDecimal(change),
    volume24h = null,
    openInterestUsd = null,
    fundingRateHourly = null,
    priceDecimals = null,
    sizeDecimals = null,
)

private fun previewPosition(
    marketIndex: Int,
    symbol: String,
    side: PerpsPositionSide,
    pnl: String,
    equity: String,
    hiddenBalance: Boolean,
): PerpsPositionUi = PerpsPosition(
    marketIndex = marketIndex,
    symbol = symbol,
    iconUrl = null,
    side = side,
    margin = BigDecimal("10"),
    unrealizedPnl = BigDecimal(pnl),
    equity = BigDecimal(equity),
    leverage = 10,
).toUi(hiddenBalance)
