package com.tonapps.swap.screens.swap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.core.components.SlippageSelector
import com.tonapps.deposit.multicoin.screens.confirm.FeeMethodPickerContent
import com.tonapps.deposit.multicoin.screens.confirm.FeeMethodPickerLeading
import com.tonapps.deposit.multicoin.screens.confirm.FeeMethodPickerOption
import com.tonapps.deposit.multicoin.screens.confirm.FeeMethodPickerSheet
import com.tonapps.deposit.multicoin.screens.confirm.FeeQuotingCell
import com.tonapps.deposit.multicoin.screens.confirm.HeadlessSwapConfirm
import com.tonapps.deposit.multicoin.screens.confirm.SwapFeePlan
import com.tonapps.deposit.multicoin.screens.confirm.UnavailableFeeCell
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.errorText
import com.tonapps.deposit.multicoin.screens.confirm.fee.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeLogic
import com.tonapps.deposit.multicoin.screens.confirm.feeErrorText
import com.tonapps.deposit.multicoin.screens.confirm.minReceived
import com.tonapps.deposit.multicoin.screens.confirm.toPickerOption
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.exchange.SwapSlippage
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLargeItemSubtitle
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonButtonCellDefaults
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.MoonPropertyTitle
import ui.components.moon.cell.MoonPropertyValue
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.preview.ThemedPreview
import ui.theme.UIKit
import kotlin.time.Duration.Companion.seconds

private sealed interface SettingsFeeRow {
    data object Quoting : SettingsFeeRow
    data class Unavailable(val onRetry: () -> Unit) : SettingsFeeRow
    data class Ready(
        val value: String,
        val method: String,
        val choosable: Boolean,
        val shortage: String?,
        val onClick: (() -> Unit)?,
    ) : SettingsFeeRow
}

@Composable
internal fun SwapSettingsSheet(
    feature: SwapFeature,
    confirm: HeadlessSwapConfirm?,
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
    onTopUp: (assetId: String) -> Unit,
) {
    val lastQuote by feature.lastQuote.collectAsState()
    val isLoading by feature.isLoading.collectAsState()
    val quoteTimer by feature.quoteTimer.collectAsState()
    val slippage by feature.slippage.collectAsState()
    val selectedSlippageBps by feature.selectedSlippageBps.collectAsState()
    val holdsQuote by feature.sendHoldsQuote.collectAsState()
    val feeLine by feature.feeLine.collectAsState()
    val button by feature.swapButton.collectAsState()
    val notice by feature.notice.collectAsState()
    val send by feature.send.collectAsState()

    val prepared = confirm?.prepared?.collectAsState()?.value
    val lastTx = remember { mutableStateOf<PendingTransaction?>(null) }
    SideEffect {
        prepared?.let { lastTx.value = it }
    }
    val tx = prepared ?: lastTx.value
    val confirmFeature = confirm?.feature
    val loading = confirmFeature?.pendingLoader?.collectAsState()?.value == true
    val quoting = confirmFeature?.feeQuoting?.collectAsState()?.value == true
    val pickedFeeId = confirmFeature?.pickedFeeId?.collectAsState()?.value
    val error = confirmFeature?.confirmationError?.collectAsState()?.value?.takeIf { !loading }
    val committed = confirmFeature?.txCommitted?.collectAsState()?.value == true
    var showPicker by remember { mutableStateOf(false) }

    val selected = remember(tx, pickedFeeId) { tx?.fee?.resolve(pickedFeeId) }
    val unpaid = (send?.plan as? SwapFeePlan.Ready)?.summary?.isUnpaid == true
    val speed = (tx?.quote?.estimatedSeconds ?: lastQuote?.quote?.estimatedSeconds)?.let { seconds ->
        when (val minutes = seconds.seconds.inWholeMinutes) {
            0L -> stringResource(Localization.swap_speed_seconds, String.format(CurrencyFormatter.locale, "%d", seconds))
            else -> stringResource(Localization.swap_speed_minutes, String.format(CurrencyFormatter.locale, "%d", minutes))
        }
    }
    val walletId = tx?.wallet?.id
    val topUp: (TxFee) -> Unit = { option ->
        when (val account = option.account) {
            is FeeAccount.Keeper -> walletId?.let { onOpenBattery(it, BatteryNativeFrom.InsufficientFunds) }
            is FeeAccount.Chain -> onTopUp(account.value.asset.id)
        }
    }

    val feeRow = when {
        confirmFeature == null || quoting -> SettingsFeeRow.Quoting
        prepared == null && error != null -> SettingsFeeRow.Unavailable(onRetry = { confirm.retryFees() })
        tx == null -> SettingsFeeRow.Quoting
        tx.isFeeUnavailable -> SettingsFeeRow.Unavailable(onRetry = { confirm.retryFees() })
        else -> tx.settingsFeeRow(
            selected = selected,
            unpaid = unpaid,
            onPick = { showPicker = true }.takeIf { !committed },
            onTopUp = { option -> topUp(option) },
            onTopUpEnergy = { assetId -> onTopUp(assetId) },
        )
    }
    val reserve = selected?.takeIf { it.isBattery }?.reserve?.let { chargesText(it) }

    val feeMessage = when (tx?.isFeeUnavailable) {
        true -> error?.feeErrorText
        else -> error?.errorText?.takeIf { !unpaid }
    }
    val quoteMessage = (feeLine as? SwapFeeLine.QuoteFailed)?.let { stringResource(it.messageRes) }

    val router = rememberDialogNavigator { feature.sendAction(SwapAction.CloseDetails) }
    MoonModalDialog(navigator = router) {
        SwapSettingsContent(
            rateLabel = prepared?.rateLabel ?: lastQuote?.rateLabel,
            rateLoading = isLoading,
            quoteTimer = quoteTimer,
            minReceived = prepared?.minReceived() ?: lastQuote?.minReceived,
            slippage = slippage,
            selectedSlippageBps = selectedSlippageBps,
            slippageEnabled = !holdsQuote,
            feeRow = feeRow,
            speed = speed,
            reserve = reserve,
            messages = listOfNotNull(feeMessage, quoteMessage),
            topUpBattery = selected?.isBattery == true && unpaid,
            button = button,
            notice = notice,
            onSelectSlippage = { feature.sendAction(SwapAction.SelectSlippage(it)) },
            onTopUpBattery = { walletId?.let { onOpenBattery(it, BatteryNativeFrom.InsufficientFunds) } },
            onSwap = { feature.onSwapClick() },
            onClose = { router.close() },
        )
    }

    if (showPicker && !committed && tx != null) {
        FeeMethodPickerSheet(
            options = tx.fee.pickerOptions.map { it.toSwapPickerOption() },
            selectedId = selected?.id,
            onSelect = { id -> tx.fee.findOption(id)?.let { confirmFeature?.selectFee(it) } },
            onActionSelect = { id -> tx.fee.findOption(id)?.let { topUp(it) } },
            onClose = { showPicker = false },
        )
    }
}

@Composable
private fun PendingTransaction.settingsFeeRow(
    selected: TxFee?,
    unpaid: Boolean,
    onPick: (() -> Unit)?,
    onTopUp: (TxFee) -> Unit,
    onTopUpEnergy: (assetId: String) -> Unit,
): SettingsFeeRow {
    val options = fee.pickerOptions
    val choosable = options.size > 1
    if (selected == null) {
        val energy = fee.energy ?: account
        val estimated = fee.estimated
        val display = estimated?.let { energy.asset.value.decimals.toDisplayUnit(it.amount) }
        return SettingsFeeRow.Ready(
            value = display?.let { Formatter.formatShort(value = it, approximate = true) } ?: "—",
            method = energy.asset.symbol,
            choosable = false,
            shortage = stringResource(Localization.swap_fee_not_enough, energy.asset.symbol).takeIf { unpaid },
            onClick = { onTopUpEnergy(energy.asset.id) }.takeIf { unpaid },
        )
    }
    val lone = options.singleOrNull()?.takeIf { !TxFeeLogic.isSufficient(it) }
    return SettingsFeeRow.Ready(
        value = when (selected.account) {
            is FeeAccount.Keeper -> chargesText(selected.chargesAfterReserve)
            is FeeAccount.Chain -> Formatter.formatShort(
                value = selected.fee.asset.decimals.toDisplayUnit(selected.fee.amount),
                approximate = true,
            )
        },
        method = when (val account = selected.account) {
            is FeeAccount.Keeper -> stringResource(Localization.battery)
            is FeeAccount.Chain -> account.value.asset.symbol
        },
        choosable = choosable,
        shortage = when {
            !unpaid -> null
            selected.isBattery -> stringResource(
                Localization.swap_fee_not_enough_charges,
                String.format(CurrencyFormatter.locale, "%d", (selected.account.balance ?: BigInteger.ZERO).intValue(exactRequired = false)),
            )
            else -> stringResource(Localization.swap_fee_not_enough, (selected.account as FeeAccount.Chain).value.asset.symbol)
        },
        onClick = when {
            lone != null -> ({ onTopUp(lone) })
            choosable -> onPick
            else -> null
        },
    )
}

@Composable
private fun chargesText(charges: BigInteger): String {
    val count = charges.intValue(exactRequired = false)
    return pluralStringResource(Plurals.battery_charges, count, String.format(CurrencyFormatter.locale, "%d", count))
}

@Composable
private fun TxFee.toSwapPickerOption(): FeeMethodPickerOption {
    val payable = TxFeeLogic.isSufficient(this)
    val option = toPickerOption(enabled = payable)
    val subtitle = when (val account = account) {
        is FeeAccount.Keeper -> stringResource(
            Localization.swap_fee_picker_battery,
            chargesText(chargesAfterReserve),
            String.format(CurrencyFormatter.locale, "%d", (account.balance ?: BigInteger.ZERO).intValue(exactRequired = false)),
        )
        is FeeAccount.Chain -> {
            val display = fee.asset.decimals.toDisplayUnit(fee.amount)
            val fiat = account.rate?.let { Formatter.formatFiat(value = display, rate = it, separator = " ", approximate = true) }
            val amount = Formatter.formatShort(value = display, asset = fee.asset)
            fiat?.let { stringResource(Localization.swap_fee_pair, it, amount) } ?: amount
        }
    }
    return option.copy(
        title = when (account) {
            is FeeAccount.Keeper -> stringResource(Localization.battery)
            is FeeAccount.Chain -> option.title
        },
        subtitle = subtitle,
        actionTitle = stringResource(Localization.deposit).takeIf { !payable },
    )
}

@Composable
private fun ColumnScope.SwapSettingsContent(
    rateLabel: String?,
    rateLoading: Boolean,
    quoteTimer: Float?,
    minReceived: Pair<String, String?>?,
    slippage: SwapSlippage?,
    selectedSlippageBps: Int?,
    slippageEnabled: Boolean,
    feeRow: SettingsFeeRow,
    speed: String?,
    reserve: String?,
    messages: List<String>,
    topUpBattery: Boolean,
    button: SwapButton,
    notice: String?,
    onSelectSlippage: (Int) -> Unit,
    onTopUpBattery: () -> Unit,
    onSwap: () -> Unit,
    onClose: () -> Unit,
) {
    MoonTopAppBarSimple(
        title = stringResource(Localization.swap_settings),
        actionIconRes = UIKitIcon.ic_close_16,
        onActionClick = onClose,
        backgroundColor = Color.Transparent,
    )

    MoonBundleCell {
        Column {
            if (rateLabel != null) {
                MoonPropertyBigCell(
                    title = { MoonPropertyTitle(title = stringResource(Localization.rate)) },
                    content = {
                        MoonPropertyValue(
                            title = rateLabel,
                            content = {
                                if (rateLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = UIKit.colorScheme.icon.primary,
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        progress = { quoteTimer ?: 1f },
                                        trackColor = UIKit.colorScheme.icon.secondary,
                                        color = UIKit.colorScheme.icon.primary,
                                        gapSize = 0.dp,
                                    )
                                }
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

            slippage?.let { options ->
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
                            slippage = options,
                            selectedBps = selectedSlippageBps,
                            onSelect = onSelectSlippage,
                            enabled = slippageEnabled,
                        )
                    },
                )
            }

            when (feeRow) {
                SettingsFeeRow.Quoting -> FeeQuotingCell()
                is SettingsFeeRow.Unavailable -> UnavailableFeeCell(onRetry = feeRow.onRetry)
                is SettingsFeeRow.Ready -> SettingsFeeCell(row = feeRow, speed = speed)
            }

            if (reserve != null) {
                MoonItemDivider()
                MoonPropertyBigCell(
                    title = {
                        MoonPropertyTitle(
                            title = stringResource(Localization.staking_temporary_reserve),
                            infoTooltip = stringResource(Localization.swap_temporary_reserve_info, reserve),
                        )
                    },
                    content = {
                        MoonItemTitle(text = stringResource(Localization.swap_fee_estimate, reserve))
                    },
                )
            }
        }
    }

    messages.forEach { message ->
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, top = 16.dp, end = 32.dp),
            color = UIKit.colorScheme.accent.red,
            style = UIKit.typography.body2,
            textAlign = TextAlign.Center,
        )
    }

    when {
        notice != null -> SwapNoticeBar(notice)
        topUpBattery -> MoonBottomButtonCell(
            text = stringResource(Localization.swap_top_up_battery),
            enabled = true,
            colors = MoonButtonCellDefaults.ButtonColorsPrimary,
            onClick = onTopUpBattery,
        )
        else -> SwapActionButton(
            button = button,
            colors = MoonButtonCellDefaults.ButtonColorsPrimary,
            onClick = onSwap,
        )
    }
}

@Composable
private fun SettingsFeeCell(row: SettingsFeeRow.Ready, speed: String?) {
    MoonItemDivider()
    MoonPropertyBigCell(
        title = {
            MoonLargeItemSubtitle(
                text = stringResource(Localization.network_fee),
                color = UIKit.colorScheme.text.secondary,
            )
        },
        content = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val accent = UIKit.colorScheme.text.accent
                val valueText = stringResource(Localization.swap_fee_pair, row.value, row.method)
                val text = remember(valueText, row.method, row.choosable, accent) {
                    val start = valueText.lastIndexOf(row.method)
                    buildAnnotatedString {
                        append(valueText)
                        if (row.choosable && start >= 0) {
                            addStyle(SpanStyle(color = accent), start, start + row.method.length)
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoonItemTitle(text = text)
                    if (row.choosable) {
                        MoonItemIcon(
                            painter = painterResource(UIKitIcon.ic_switch_16),
                            color = UIKit.colorScheme.text.accent,
                        )
                    }
                }
                when {
                    row.shortage != null -> MoonItemSubtitle(text = row.shortage, color = UIKit.colorScheme.accent.red)
                    speed != null -> MoonItemSubtitle(text = speed)
                }
            }
        },
        onClick = row.onClick,
    )
}

@Composable
private fun ColumnScope.PreviewSettings(
    feeRow: SettingsFeeRow,
    reserve: String? = null,
    topUpBattery: Boolean = false,
    button: SwapButton = SwapButton.Swap(enabled = true),
) {
    SwapSettingsContent(
        rateLabel = "1 GRAM ≈ 1.417408 USDT",
        rateLoading = false,
        quoteTimer = 0.6f,
        minReceived = "1.403234 USDT" to "≈ $1.40",
        slippage = null,
        selectedSlippageBps = 100,
        slippageEnabled = true,
        feeRow = feeRow,
        speed = "Speed: about 2 min",
        reserve = reserve,
        messages = emptyList(),
        topUpBattery = topUpBattery,
        button = button,
        notice = null,
        onSelectSlippage = {},
        onTopUpBattery = {},
        onSwap = {},
        onClose = {},
    )
}

@Preview
@Composable
private fun SwapSettingsBatteryPreview() {
    ThemedPreview {
        Column {
            PreviewSettings(
                feeRow = SettingsFeeRow.Ready("7 charges", "Battery", choosable = true, shortage = null, onClick = {}),
                reserve = "21 charges",
            )
        }
    }
}

@Preview
@Composable
private fun SwapSettingsCoinPreview() {
    ThemedPreview {
        Column {
            PreviewSettings(
                feeRow = SettingsFeeRow.Ready("≈ 0.03", "GRAM", choosable = true, shortage = null, onClick = {}),
            )
        }
    }
}

@Preview
@Composable
private fun SwapSettingsNotEnoughPreview() {
    ThemedPreview {
        Column {
            PreviewSettings(
                feeRow = SettingsFeeRow.Ready("≈ 0.03", "GRAM", choosable = false, shortage = "Not enough GRAM", onClick = {}),
                button = SwapButton.FeeUnpaid,
            )
        }
    }
}

@Preview
@Composable
private fun SwapSettingsTopUpBatteryPreview() {
    ThemedPreview {
        Column {
            PreviewSettings(
                feeRow = SettingsFeeRow.Ready("7 charges", "Battery", choosable = true, shortage = "Not enough · 0 available", onClick = {}),
                topUpBattery = true,
            )
        }
    }
}

@Preview
@Composable
private fun SwapFeePickerPreview() {
    ThemedPreview {
        Column {
            FeeMethodPickerContent(
                options = listOf(
                    FeeMethodPickerOption(
                        id = "battery",
                        title = "Battery",
                        subtitle = "7 charges · 771 available",
                        leading = FeeMethodPickerLeading.Battery,
                    ),
                    FeeMethodPickerOption(
                        id = "gram",
                        title = "GRAM",
                        subtitle = "≈ $0.0365 · 0.03 GRAM",
                        leading = FeeMethodPickerLeading.Image(url = ""),
                    ),
                    FeeMethodPickerOption(
                        id = "usdt",
                        title = "USDT",
                        subtitle = "≈ $0.05 · 0.05 USDT",
                        leading = FeeMethodPickerLeading.Image(url = ""),
                        enabled = false,
                        actionTitle = "Deposit",
                    ),
                ),
                selectedId = "battery",
                onSelect = {},
                onActionSelect = {},
                onClose = {},
            )
        }
    }
}
