package com.tonapps.tonkeeper.ui.screen.staking.viewer

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.tonapps.wallet.localization.Localization
import ui.components.moon.ButtonColorsPrimary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonBottomBar

@Composable
internal fun StakeViewerActionsBar(
    unstakeEnabled: Boolean,
    stakeEnabled: Boolean,
    onUnstakeClick: () -> Unit,
    onStakeClick: () -> Unit,
    backgroundColor: Color,
) {
    MoonBottomBar(backgroundColor = backgroundColor) {
        ActionButton(Localization.unstake, unstakeEnabled, onUnstakeClick)
        ActionButton(Localization.stake, stakeEnabled, onStakeClick)
    }
}

@Composable
private fun RowScope.ActionButton(
    textRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    MoonAccentButton(
        modifier = Modifier.weight(1f),
        text = stringResource(textRes),
        size = ButtonSizeLarge,
        buttonColors = ButtonColorsPrimary,
        enabled = enabled,
        onClick = onClick,
    )
}