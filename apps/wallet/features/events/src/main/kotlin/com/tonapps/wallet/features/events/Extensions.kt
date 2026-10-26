package com.tonapps.wallet.features.events

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.icu.TransactionDateFormatter
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.features.events.data.HistoryEventEntity
import com.tonapps.wallet.features.events.data.PerpsEventKind
import com.tonapps.wallet.features.events.data.PerpsResultEntity
import com.tonapps.wallet.features.events.data.PerpsSide
import com.tonapps.wallet.localization.Localization
import io.walletapi.models.ActivityType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale


@Composable
internal fun HistoryEventEntity.title(): String = stringResource(completedTitleRes())

@Composable
internal fun HistoryEventEntity.statusTitle(): String {
    val titleRes = if (isPending) {
        pendingTitleRes()
    } else {
        completedTitleRes()
    }
    return stringResource(titleRes)
}

private fun HistoryEventEntity.completedTitleRes(): Int {
    perpsKind?.let { return it.titleRes() }
    return when (activityType) {
        ActivityType.send -> Localization.sent
        ActivityType.receive, ActivityType.mint -> Localization.received
        ActivityType.swap -> Localization.swap_event
        ActivityType.stake -> Localization.stake
        ActivityType.unstake -> Localization.unstake
        ActivityType.burn -> Localization.burned
        ActivityType.dns_renew -> Localization.domain_renew
        else -> if (isIncoming) {
            Localization.received
        } else {
            Localization.sent
        }
    }
}

private fun HistoryEventEntity.pendingTitleRes(): Int {
    perpsKind?.let { return it.titleRes() }
    return when (activityType) {
        ActivityType.send -> Localization.sending
        ActivityType.receive, ActivityType.mint -> Localization.receiving
        ActivityType.swap -> Localization.swapping
        ActivityType.stake -> Localization.staking
        ActivityType.unstake -> Localization.unstaking
        ActivityType.burn -> Localization.burning
        ActivityType.dns_renew -> Localization.domain_renewing
        else -> if (isIncoming) {
            Localization.receiving
        } else {
            Localization.sending
        }
    }
}

private fun PerpsEventKind.titleRes(): Int = when (this) {
    is PerpsEventKind.Opened -> when (side) {
        PerpsSide.Long -> Localization.history_perps_opened_long
        PerpsSide.Short -> Localization.history_perps_opened_short
        null -> Localization.history_perps_opened
    }
    is PerpsEventKind.Increased -> when (side) {
        PerpsSide.Long -> Localization.history_perps_increased_long
        PerpsSide.Short -> Localization.history_perps_increased_short
        null -> Localization.history_perps_increased
    }
    is PerpsEventKind.Reduced -> when (side) {
        PerpsSide.Long -> Localization.history_perps_reduced_long
        PerpsSide.Short -> Localization.history_perps_reduced_short
        null -> Localization.history_perps_reduced
    }
    is PerpsEventKind.Closed -> when (side) {
        PerpsSide.Long -> Localization.history_perps_closed_long
        PerpsSide.Short -> Localization.history_perps_closed_short
        null -> Localization.history_perps_closed
    }
    is PerpsEventKind.Liquidated -> when (side) {
        PerpsSide.Long -> Localization.history_perps_liquidated_long
        PerpsSide.Short -> Localization.history_perps_liquidated_short
        null -> Localization.history_perps_liquidated
    }
    PerpsEventKind.TakeProfit -> Localization.history_perps_tp_executed
    PerpsEventKind.StopLoss -> Localization.history_perps_sl_executed
    PerpsEventKind.Deposit -> Localization.deposit
    PerpsEventKind.Withdrawal -> Localization.withdraw
}

private fun PerpsEventKind.iconRes(): Int = when (this) {
    is PerpsEventKind.Opened, is PerpsEventKind.Increased -> UIKitIcon.ic_open_lock_28
    is PerpsEventKind.Reduced, is PerpsEventKind.Closed -> UIKitIcon.ic_lock_28
    PerpsEventKind.TakeProfit -> UIKitIcon.ic_chart_up_28
    PerpsEventKind.StopLoss -> UIKitIcon.ic_chart_down_28
    is PerpsEventKind.Liquidated -> UIKitIcon.ic_fire_28
    PerpsEventKind.Deposit -> UIKitIcon.ic_tray_arrow_down_28
    PerpsEventKind.Withdrawal -> UIKitIcon.ic_tray_arrow_up_28
}

@Composable
internal fun HistoryEventEntity.perpsSubtitle(labelsBalanceChanges: Boolean = true): String? = when (perpsKind) {
    is PerpsEventKind.Opened, is PerpsEventKind.Increased, is PerpsEventKind.Reduced,
    is PerpsEventKind.Closed, is PerpsEventKind.Liquidated,
    PerpsEventKind.TakeProfit, PerpsEventKind.StopLoss -> perps?.symbol?.let { symbol ->
        stringResource(Localization.history_perps_subtitle, symbol)
    }
    PerpsEventKind.Deposit -> if (labelsBalanceChanges) {
        stringResource(Localization.history_perps_to_perps)
    } else {
        null
    }
    PerpsEventKind.Withdrawal -> if (labelsBalanceChanges) {
        stringResource(Localization.history_perps_from_perps)
    } else {
        null
    }
    null -> null
}

@Composable
internal fun HistoryEventEntity.perpsResultText(): String? = perpsResult?.let { result ->
    remember(result) { result.formatted() }
}

@Composable
internal fun HistoryEventEntity.perpsFeeText(): String? = perpsFee?.let { fee ->
    remember(fee) { fee.formatted() }
}

private fun PerpsResultEntity.formatted(): String {
    return if (isDollar) {
        formatUsdAmount(value.value)
    } else {
        CurrencyFormatter.format(symbol, value, replaceSymbol = false).toString()
    }
}

@Composable
internal fun HistoryEventEntity.iconPainter(): Painter {
    val provider = HistoryStakingProvider.of(this)
    if (provider != null) {
        return painterResource(provider.iconRes)
    }
    if (isFailed) {
        return painterResource(UIKitIcon.ic_exclamationmark_circle_28)
    }
    perpsKind?.let { return painterResource(it.iconRes()) }
    return when (activityType) {
        ActivityType.send, ActivityType.stake, ActivityType.unstake, ActivityType.burn ->
            painterResource(UIKitIcon.ic_tray_arrow_up_28)
        ActivityType.receive, ActivityType.mint ->
            painterResource(UIKitIcon.ic_tray_arrow_down_28)
        ActivityType.swap ->
            painterResource(UIKitIcon.ic_swap_horizontal_outline_28)
        ActivityType.dns_renew ->
            painterResource(UIKitIcon.ic_update_24)
        else -> if (isIncoming) {
            painterResource(UIKitIcon.ic_tray_arrow_down_28)
        } else {
            painterResource(UIKitIcon.ic_tray_arrow_up_28)
        }
    }
}

internal fun HistoryEventEntity.hasStakingProviderIcon(): Boolean =
    HistoryStakingProvider.of(this) != null

@Composable
internal fun HistoryEventEntity.formattedTime(): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val zone = ZoneId.systemDefault()
    val zdt = blockTime.atZoneSameInstant(zone)
    return DateTimeFormatter.ofPattern("HH:mm", locale).format(zdt)
}

@Composable
internal fun HistoryEventEntity.formattedDateTime(): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val zdt = blockTime.atZoneSameInstant(ZoneId.systemDefault())
    return TransactionDateFormatter.format(zdt, locale, TransactionDateFormatter.DateFrom.Today)
}
