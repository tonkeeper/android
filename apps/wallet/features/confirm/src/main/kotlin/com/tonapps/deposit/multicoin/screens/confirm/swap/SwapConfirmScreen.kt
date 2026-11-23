package com.tonapps.deposit.multicoin.screens.confirm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.chainkit.core.chain.model.num.toDisplayUnit
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.core.components.SlippageSelector
import com.tonapps.core.components.assetImageUrl
import com.tonapps.core.components.tokenChainImageUrl
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.Signing
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.exchange.SwapSlippage
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonCircleIcon
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.MoonPropertyTitle
import ui.components.moon.cell.MoonPropertyValue
import ui.components.moon.cell.TextCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.theme.UIKit

@Composable
fun SwapConfirmScreen(
    feature: SwapConfirmFeature,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
    onSendSuccess: () -> Unit,
    onConfirm: () -> Unit = {},
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
    onTopUp: (assetId: String) -> Unit,
) {
    ConfirmScreenScaffold(
        feature = feature,
        title = stringResource(Localization.confirm_swap),
        onClose = onClose,
        onBack = onBack,
        onSendSuccess = onSendSuccess,
        onConfirm = onConfirm,
        onTopUp = onTopUp,
        onOpenBattery = onOpenBattery,
    ) { tx ->
        SwapConfirmBody(
            feature = feature,
            tx = tx,
            onOpenBattery = onOpenBattery,
        )
    }
}

@Composable
private fun SwapConfirmBody(
    feature: SwapConfirmFeature,
    tx: PendingTransaction,
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
) {
    val trade = (tx.signing as? Signing.Tx)
        ?.value as? Transaction.Swap
        ?: return

    val from = tx.account
    val to = tx.destination ?: return

    val quote = tx.quote

    val buyDisplay = quote?.let { trade.destination.asset.toDisplayUnit(it.buyBaseAmount) }

    ConfirmSwapHeader(
        assetFrom = from.asset,
        fromAmount = tx.sellAmount().orEmpty(),
        assetTo = to.asset,
        toAmount = buyDisplay?.let {
            Formatter.formatShort(
                value = it,
                asset = trade.destination.asset,
                approximate = true,
            )
        }.orEmpty(),
    )

    Spacer(Modifier.height(16.dp))

    val rateProgress by feature.swapQuoteCountdown.collectAsState()
    val slippageOptions by feature.slippage.collectAsState()
    val selectedSlippageBps by feature.selectedSlippageBps.collectAsState()
    SwapRouteRows(
        rateLabel = tx.rateLabel,
        rateProgress = rateProgress,
        minReceived = tx.minReceived(),
        slippage = slippageOptions,
        selectedSlippageBps = selectedSlippageBps,
        onSelectSlippage = feature::selectSlippage,
        priceImpact = tx.priceImpact(),
    ) {
        TxFeeRow(
            feature = feature,
            tx = tx,
            onOpenBattery = onOpenBattery,
        )
    }
}

/**
 * The route's rows, in the swap confirm's card: the rate counting down to the next quote, what is
 * received at least, the slippage to pick, a price impact worth telling, and [feeRow] last.
 */
@Composable
internal fun SwapRouteRows(
    rateLabel: String?,
    rateProgress: Float,
    minReceived: Pair<String, String?>?,
    slippage: SwapSlippage?,
    selectedSlippageBps: Int?,
    onSelectSlippage: (Int) -> Unit,
    priceImpact: PriceImpact?,
    feeRow: @Composable () -> Unit,
) {
    MoonBundleCell {
        Column {
            if (rateLabel != null) {
                MoonPropertyBigCell(
                    title = {
                        MoonPropertyTitle(title = stringResource(Localization.rate))
                    },
                    content = {
                        MoonPropertyValue(
                            title = rateLabel,
                            content = {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    progress = { rateProgress },
                                    trackColor = UIKit.colorScheme.icon.secondary,
                                    color = UIKit.colorScheme.icon.primary,
                                    gapSize = 0.dp,
                                )
                            },
                        )
                    },
                )
            }

            if (minReceived != null) {
                MoonItemDivider()
                MoonPropertyBigCell(
                    title = stringResource(Localization.min_received),
                    value = minReceived.first,
                    valueDescription = minReceived.second,
                )
            }

            if (slippage != null) {
                MoonItemDivider()
                MoonPropertyBigCell(
                    title = {
                        MoonPropertyTitle(
                            title = stringResource(Localization.slippage),
                            infoTooltip = stringResource(Localization.swap_slippage_info),
                        )
                    },
                    content = {
                        SlippageSelector(
                            slippage = slippage,
                            selectedBps = selectedSlippageBps,
                            onSelect = onSelectSlippage,
                        )
                    },
                )
            }

            if (priceImpact != null) {
                MoonItemDivider()
                PriceImpactCell(priceImpact)
            }

            feeRow()
        }
    }
}

@Composable
internal fun ConfirmSwapHeader(
    assetFrom: AssetEntity,
    fromAmount: String,
    assetTo: AssetEntity,
    toAmount: String,
) {
    Box {
        Column(Modifier.fillMaxSize()) {
            MoonBundleCell {
                Row {
                    TextCell(
                        image = {
                            val imageUrl = assetFrom.assetImageUrl()
                            val chainImageUrl = assetFrom.tokenChainImageUrl()

                            MoonCutBadgedBox(
                                badge = if (chainImageUrl != null) {
                                    { MoonItemImage(image = chainImageUrl, size = 20.dp) }
                                } else {
                                    null
                                },
                                direction = BadgeDirection.EndBottom,
                            ) {
                                MoonItemImage(
                                    image = imageUrl,
                                    size = 44.dp,
                                )
                            }
                        },
                        title = { MoonItemSubtitle("You send") },
                        subtitle = {
                            Text(
                                text = fromAmount,
                                style = UIKit.typography.num2,
                                color = UIKit.colorScheme.text.primary,
                            )
                        },
                        minHeight = 78.dp,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            MoonBundleCell {
                TextCell(
                    image = {
                        val imageUrl = assetTo.assetImageUrl()
                        val chainImageUrl = assetTo.tokenChainImageUrl()

                        MoonCutBadgedBox(
                            badge = if (chainImageUrl != null) {
                                { MoonItemImage(image = chainImageUrl, size = 20.dp) }
                            } else {
                                null
                            },
                            direction = BadgeDirection.EndBottom,
                        ) {
                            MoonItemImage(
                                image = imageUrl,
                                size = 44.dp,
                            )
                        }
                    },
                    title = { MoonItemSubtitle("You receive") },
                    subtitle = {
                        Text(
                            text = toAmount,
                            style = UIKit.typography.num2,
                            color = UIKit.colorScheme.text.primary,
                        )
                    },
                    minHeight = 78.dp,
                )
            }
        }

        MoonCircleIcon(
            modifier = Modifier
                .padding(end = 40.dp)
                .align(Alignment.CenterEnd),
            painter = painterResource(UIKitIcon.ic_arrow_down_16),
            size = 40.dp,
            color = UIKit.colorScheme.buttonTertiary.primaryBackground,
        )
    }
}
