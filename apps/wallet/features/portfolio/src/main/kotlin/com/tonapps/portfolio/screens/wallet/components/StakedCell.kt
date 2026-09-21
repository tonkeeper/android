package com.tonapps.portfolio.screens.wallet.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.icu.Coins
import com.tonapps.portfolio.screens.wallet.StakedUi
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.localization.Localization
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.delay
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.TextCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.painterResource
import ui.theme.UIKit

@Composable
fun StakedCell(
    item: StakedUi,
    position: MoonBundlePosition = MoonBundlePosition.Default,
    onClick: () -> Unit = {},
    onReadyWithdrawClick: () -> Unit = {},
) {
    val balance = if (item.hiddenBalance) HIDDEN_BALANCE else item.balanceFormat.toString()
    val fiat = if (item.hiddenBalance) HIDDEN_BALANCE else item.fiatFormat.toString()
    val readyWithdrawClickable =
        item.readyWithdraw > Coins.ZERO &&
            item.poolImplementation != StakingPool.Implementation.LiquidTF

    MoonBundleCell(position = position) {
        TextCell(
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoonItemTitle(text = stringResource(Localization.staked))
                    MoonItemTitle(text = balance)
                }
            },
            subtitle = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MoonItemSubtitle(text = item.poolName)
                        MoonItemSubtitle(text = fiat)
                    }
                    if (item.hasStatus) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    color = UIKit.colorScheme.background.contentTint,
                                    shape = RoundedCornerShape(16.dp),
                                )
                                .then(
                                    if (readyWithdrawClickable) {
                                        Modifier.clickable(onClick = onReadyWithdrawClick)
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            StakedStatusLabel(item = item)
                        }
                    }
                }
            },
            image = {
                MoonCutBadgedBox(
                    badge = {
                        MoonItemImage(
                            painter = painterResource(StakingPool.getIcon(item.poolImplementation)),
                            size = 22.dp,
                        )
                    },
                    direction = BadgeDirection.EndBottom,
                ) {
                    MoonItemImage(
                        painter = painterResource(UIKitIcon.ic_gram_with_bg),
                        size = 44.dp,
                    )
                }
            },
            onClick = onClick,
            minHeight = 76.dp,
        )
    }
}

private val StakedUi.hasStatus: Boolean
    get() = readyWithdraw > Coins.ZERO ||
        pendingDeposit > Coins.ZERO ||
        pendingWithdraw > Coins.ZERO

@Composable
private fun StakedStatusLabel(item: StakedUi) {
    when {
        item.readyWithdraw > Coins.ZERO -> {
            val amount = item.readyWithdrawFormat?.toString() ?: return
            StatusSubtitle(stringResource(Localization.staking_ready_withdraw, amount))
        }

        item.pendingDeposit > Coins.ZERO -> {
            val amount = item.pendingDepositFormat?.toString() ?: return
            CountdownStatusSubtitle(
                textRes = Localization.staking_pending_deposit,
                amount = amount,
                cycleEnd = item.cycleEnd,
            )
        }

        item.pendingWithdraw > Coins.ZERO &&
            item.poolImplementation == StakingPool.Implementation.LiquidTF -> {
            val amount = item.pendingWithdrawFormat?.toString() ?: return
            StatusSubtitle(stringResource(Localization.staking_pending_withdraw_liquid, amount))
        }

        item.pendingWithdraw > Coins.ZERO -> {
            val amount = item.pendingWithdrawFormat?.toString() ?: return
            CountdownStatusSubtitle(
                textRes = Localization.staking_pending_withdraw,
                amount = amount,
                cycleEnd = item.cycleEnd,
            )
        }
    }
}

@Composable
private fun CountdownStatusSubtitle(
    @StringRes textRes: Int,
    amount: String,
    cycleEnd: Long,
) {
    val state by rememberCountDownState(cycleEnd)
    StatusSubtitle(stringResource(textRes, amount, formatCycleEnd(state.count)))
}

@Composable
private fun StatusSubtitle(text: String) {
    MoonItemSubtitle(
        text = text,
        color = UIKit.colorScheme.text.primary,
    )
}

@JvmInline
private value class CountDownState(val count: Long)

@OptIn(ExperimentalTime::class)
@Composable
private fun rememberCountDownState(cycleEnd: Long): State<CountDownState> {
    val state = remember(cycleEnd) {
        mutableStateOf(CountDownState(remainingSeconds(cycleEnd)))
    }
    LaunchedEffect(cycleEnd) {
        while (true) {
            state.value = CountDownState(remainingSeconds(cycleEnd))
            delay(1.seconds)
        }
    }
    return state
}

@OptIn(ExperimentalTime::class)
private fun remainingSeconds(cycleEnd: Long): Long {
    val now = Clock.System.now()
    val estimate = Instant.fromEpochSeconds(cycleEnd).coerceAtLeast(now)
    return (estimate - now).inWholeSeconds
}

private fun formatCycleEnd(remainingSeconds: Long): String {
    val hours = remainingSeconds / 3600
    val minutes = (remainingSeconds % 3600) / 60
    val seconds = remainingSeconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}
