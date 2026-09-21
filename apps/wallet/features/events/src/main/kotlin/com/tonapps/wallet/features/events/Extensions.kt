package com.tonapps.wallet.features.events

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.features.events.data.HistoryEventEntity
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

private fun HistoryEventEntity.completedTitleRes(): Int = when (activityType) {
    ActivityType.send -> Localization.sent
    ActivityType.receive, ActivityType.mint -> Localization.received
    ActivityType.swap -> Localization.swap
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

private fun HistoryEventEntity.pendingTitleRes(): Int = when (activityType) {
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

@Composable
internal fun HistoryEventEntity.iconPainter(): Painter {
    val provider = HistoryStakingProvider.of(this)
    if (provider != null) {
        return painterResource(provider.iconRes)
    }
    if (isFailed) {
        return painterResource(UIKitIcon.ic_exclamationmark_circle_28)
    }
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
