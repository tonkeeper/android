package com.tonapps.portfolio.screens.wallet.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.RStr
import ui.components.moon.cell.MoonBundleTitleCell
import ui.painterResource
import ui.theme.UIKit
import ui.theme.modifiers.shimmer

@Composable
internal fun AssetsHeader(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onManageClick: () -> Unit,
) {
    MoonBundleTitleCell(
        modifier = modifier,
        title = stringResource(RStr.crypto),
        onClick = onClick,
        content = {
            SettingsButton(onManageClick)
        },
    )
}

@Composable
private fun SettingsButton(
    onManageClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onManageClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(RStr.manage),
            style = UIKit.typography.label2,
            color = UIKit.colorScheme.text.secondary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            modifier = Modifier,
            painter = painterResource(UIKitIcon.ic_sliders_16),
            tint = UIKit.colorScheme.icon.secondary,
            contentDescription = null
        )
    }
}

@Composable
internal fun AssetsHeader(
    modifier: Modifier = Modifier,
    shimmerPhase: Float,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "          ",
            style = UIKit.typography.label1,
            modifier = Modifier.shimmer(shimmerPhase),
        )
        Text(
            text = "            ",
            style = UIKit.typography.label2,
            modifier = Modifier.shimmer(shimmerPhase),
        )
    }
}