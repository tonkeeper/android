package com.tonapps.perps.screens.withdraw

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.extensions.fiatSymbol
import com.tonapps.mvi.graph.KState
import com.tonapps.paging.collectAsStateWorkaround
import com.tonapps.perps.data.USD_CURRENCY
import com.tonapps.perps.data.formatTokenCompact
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.funding.PerpsFundingAmount
import com.tonapps.perps.funding.PerpsWithdrawDestination
import com.tonapps.perps.funding.PerpsWithdrawService
import com.tonapps.perps.screens.common.PerpsFundingTopBar
import com.tonapps.perps.screens.deposit.PerpsAmountInputCell
import com.tonapps.perps.screens.deposit.SourceImage
import com.tonapps.perps.screens.deposit.networkName
import com.tonapps.perps.screens.deposit.showsNetwork
import com.tonapps.perps.screens.deposit.sourceSubtitle
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonBadgeButton
import ui.components.moon.MoonCircleIcon
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonSmallItemTitle
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.MoonRetryCell
import ui.components.moon.cell.TextCell
import ui.components.moon.cell.TextCheckCell
import ui.components.moon.container.MoonScaffold
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.painterResource
import ui.theme.UIKit

@Composable
fun PerpsWithdrawScreen(
    feature: PerpsWithdrawFeature,
    onClose: () -> Unit,
    onReview: (ConfirmRequest) -> Unit,
) {
    val available by feature.available.collectAsStateWorkaround()
    val destinations by feature.destinations.collectAsStateWorkaround()
    val selected by feature.selected.collectAsStateWorkaround()
    val amountInput by feature.amountInput.collectAsStateWorkaround()
    val inputInToken by feature.inputInToken.collectAsStateWorkaround()
    val summary by feature.summary.collectAsStateWorkaround()
    val withdrawInFlight by feature.withdrawInFlight.collectAsStateWorkaround()
    var showMethods by remember { mutableStateOf(false) }

    val current = summary
    val target = selected?.destination
    val canReview = current?.canReview == true && target != null && !withdrawInFlight
    val review = {
        if (current != null && target != null && canReview) {
            feature.confirmRequest(current.amountUnits, target.assetId)?.let(onReview)
        }
    }

    MoonScaffold(
        modifier = Modifier
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        topBar = {
            PerpsFundingTopBar(
                title = stringResource(Localization.perps_withdraw_title),
                onClose = onClose,
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .nestedScroll(rememberNestedScrollInteropConnection()),
            ) {
                val receiveSymbol = target?.let(::destinationTitle)
                PerpsAmountInputCell(
                    amount = amountInput,
                    currencyCode = if (inputInToken) {
                        receiveSymbol.orEmpty()
                    } else {
                        USD_CURRENCY
                    },
                    pillText = when {
                        current == null || receiveSymbol == null -> null
                        inputInToken -> formatUsdAmount(PerpsFundingAmount.toDecimal(current.amountUnits, PerpsFundingAmount.USDC_DECIMALS))
                        else -> current.receiveAmount?.let { formatTokenCompact(it, receiveSymbol) }
                    },
                    maxFractionDigits = if (inputInToken && target != null) {
                        target.receiveInputDecimals
                    } else {
                        PerpsFundingAmount.USDC_DECIMALS
                    },
                    onAmountChange = { feature.sendAction(PerpsWithdrawAction.SetAmount(it)) },
                    onSwap = { feature.sendAction(PerpsWithdrawAction.ToggleInput) }.takeIf { target?.usdPrice != null },
                    onDone = review,
                    currencyPrefix = USD_CURRENCY.fiatSymbol().takeIf { !inputInToken },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MoonBadgeButton(
                        content = { MoonSmallItemTitle(stringResource(Localization.max).uppercase()) },
                        modifier = Modifier.height(MAX_BUTTON_HEIGHT),
                        onClick = { feature.sendAction(PerpsWithdrawAction.Max) },
                    )
                    val availableFormat = available.state
                    when {
                        withdrawInFlight -> MoonItemSubtitle(
                            text = stringResource(Localization.perps_withdraw_in_flight),
                            color = UIKit.colorScheme.accent.orange,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                            maxLines = 2,
                        )

                        current?.insufficient == true -> MoonItemSubtitle(
                            text = stringResource(Localization.insufficient_balance),
                            color = UIKit.colorScheme.accent.red,
                        )

                        current?.belowMinimum == true -> MoonItemSubtitle(
                            text = stringResource(
                                Localization.perps_withdraw_min_amount,
                                PerpsFundingAmount.declaredAmount(
                                    current.minAmountUnits ?: PerpsWithdrawService.DEFAULT_MIN_UNITS,
                                    PerpsFundingAmount.USDC_DECIMALS,
                                ),
                            ),
                            color = UIKit.colorScheme.accent.red,
                        )

                        availableFormat != null -> MoonItemSubtitle(
                            text = stringResource(Localization.available, availableFormat),
                            color = UIKit.colorScheme.text.secondary,
                        )

                        available !is KState.Loading -> MoonItemSubtitle(
                            text = stringResource(Localization.perps_withdraw_available_failed),
                            color = UIKit.colorScheme.accent.red,
                            modifier = Modifier.clickable { feature.sendAction(PerpsWithdrawAction.Reload) },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                val keyboardController = LocalSoftwareKeyboardController.current
                DestinationCell(
                    destinations = destinations,
                    selected = selected,
                    onRetry = { feature.sendAction(PerpsWithdrawAction.Reload) },
                    onClick = {
                        keyboardController?.hide()
                        showMethods = true
                    },
                )
            }

            MoonBottomButtonCell(
                text = stringResource(Localization.perps_withdraw_review),
                enabled = canReview,
                onClick = review,
            )
        }
    }

    val loaded = destinations.state
    if (showMethods && target != null && loaded != null) {
        PerpsWithdrawMethodDialog(
            destinations = loaded,
            selectedAssetId = target.assetId,
            onSelect = { feature.sendAction(PerpsWithdrawAction.SelectDestination(it)) },
            onClose = { showMethods = false },
        )
    }
}

@Composable
private fun DestinationCell(
    destinations: KState<List<PerpsWithdrawDestinationUi>?>,
    selected: PerpsWithdrawDestinationUi?,
    onRetry: () -> Unit,
    onClick: () -> Unit,
) {
    when {
        selected != null -> MoonBundleCell {
            TextCell(
                title = destinationTitle(selected.destination),
                subtitle = selected.source?.let { sourceSubtitle(it) },
                onClick = onClick,
                content = { MoonItemIcon(painterResource(UIKitIcon.ic_switch_16)) },
                minHeight = 76.dp,
                image = { DestinationImage(selected.destination) },
            )
        }

        destinations is KState.Loading -> MoonLoaderCell(height = 76.dp)
        else -> MoonRetryCell(
            message = stringResource(Localization.perps_withdraw_account_unavailable),
            buttonText = stringResource(Localization.perps_retry),
            onRetry = onRetry,
        )
    }
}

@Composable
internal fun DestinationImage(destination: PerpsWithdrawDestination, size: Dp = 44.dp) {
    val source = destination.source
    if (source != null) {
        SourceImage(source = source, size = size)
    } else {
        MoonCircleIcon(
            painter = painterResource(UIKitIcon.ic_dollar_outline_minus_16),
            size = size,
            iconSize = size * FALLBACK_ICON_RATIO,
            color = UIKit.colorScheme.accent.blue.copy(alpha = ICON_BACKGROUND_ALPHA),
            tint = UIKit.colorScheme.accent.blue,
        )
    }
}

@Composable
private fun PerpsWithdrawMethodDialog(
    destinations: List<PerpsWithdrawDestinationUi>,
    selectedAssetId: String,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)

    MoonModalDialog(navigator = navigator) {
        PerpsFundingTopBar(
            title = stringResource(Localization.choose_asset),
            onClose = { navigator.close() },
        )

        MoonSurface {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MoonBundleCell {
                    Column {
                        destinations.forEachIndexed { index, item ->
                            val destination = item.destination
                            if (index > 0) {
                                MoonItemDivider()
                            }
                            TextCheckCell(
                                title = destinationTitle(destination),
                                subtitle = item.source?.let { sourceSubtitle(it) },
                                isChecked = destination.assetId == selectedAssetId,
                                onCheckedChange = {
                                    navigator.close()
                                    onSelect(destination.assetId)
                                },
                                minHeight = 76.dp,
                                image = { DestinationImage(destination) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

internal fun destinationTitle(destination: PerpsWithdrawDestination): String {
    return destination.source?.symbol ?: PerpsWithdrawService.USDC_SYMBOL
}

internal fun destinationNetwork(destination: PerpsWithdrawDestination): String? {
    val source = destination.source
    return when {
        source != null && showsNetwork(source) -> networkName(source.account.value.chain)
        else -> null
    }
}

private val MAX_BUTTON_HEIGHT = 36.dp
private const val ICON_BACKGROUND_ALPHA = 0.12f
private const val FALLBACK_ICON_RATIO = 0.5f
