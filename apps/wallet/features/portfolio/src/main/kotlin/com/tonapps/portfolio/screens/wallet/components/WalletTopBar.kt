package com.tonapps.portfolio.screens.wallet.components

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import ui.components.moon.MoonTopAppBar
import ui.components.moon.MoonTopAppBarIcon
import ui.painterResource

@Composable
internal fun WalletTopBar(
    modifier: Modifier = Modifier,
    wallet: McWalletEntity,
    onWalletClick: () -> Unit,
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    settingsBadge: Boolean = false,
) {
    MoonTopAppBar(
        modifier = modifier.statusBarsPadding(),
        navigation = {
            MoonTopAppBarIcon(
                painter = painterResource(UIKitIcon.ic_qr_viewfinder_outline_28),
                onClick = onScanClick,
            )
        },
        actions = {
            MoonTopAppBarIcon(
                painter = painterResource(UIKitIcon.ic_clock_outline_28),
                onClick = onHistoryClick,
            )
            MoonTopAppBarIcon(
                painter = painterResource(UIKitIcon.ic_gear_outline_28),
                onClick = onSettingsClick,
                showNotificationDot = settingsBadge,
            )
        },
        horizontalPadding = 8.dp,
        backgroundColor = Color.Transparent,
        content = {
            WalletTopBarSwitch(
                name = wallet.name,
                emoji = wallet.emoji,
                color = Color(wallet.color),
                onClick = onWalletClick,
            )
        },
    )
}
