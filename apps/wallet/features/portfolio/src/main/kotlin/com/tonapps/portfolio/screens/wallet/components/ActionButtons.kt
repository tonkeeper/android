package com.tonapps.portfolio.screens.wallet.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.portfolio.screens.wallet.WalletAction
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.painterResource
import ui.theme.UIKit

@Composable
internal fun ActionButtons(
    modifier: Modifier = Modifier,
    actions: List<WalletAction>,
    onClick: (WalletAction) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        actions.forEach { action ->
            key(action) {
                ActionButton(
                    title = action.title(),
                    painter = action.icon(),
                    onClick = { onClick(action) }
                )
            }
        }
    }
}

@Composable
private fun WalletAction.title(): String = stringResource(
    when (this) {
        WalletAction.Send -> Localization.send
        WalletAction.Deposit -> Localization.add_funds
        WalletAction.Swap -> Localization.swap
        WalletAction.Stake -> Localization.stake
    }
)

@Composable
private fun WalletAction.icon(): Painter = painterResource(
    when (this) {
        WalletAction.Send -> UIKitIcon.ic_arrow_up_outline_28
        WalletAction.Deposit -> UIKitIcon.ic_arrow_down_outline_28
        WalletAction.Swap -> UIKitIcon.ic_swap_horizontal_outline_28
        WalletAction.Stake -> UIKitIcon.ic_staking_outline_28
    }
)

@Composable
private fun ActionButton(
    modifier: Modifier = Modifier,
    title: String,
    painter: Painter,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(UIKit.colorScheme.buttonSecondary.primaryBackground)
                .indication(
                    interactionSource = interactionSource,
                    indication = ripple()
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painter,
                tint = UIKit.colorScheme.buttonSecondary.primaryForeground,
                contentDescription = null,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            modifier = Modifier.padding(horizontal = 4.dp),
            text = title,
            style = UIKit.typography.label3,
            color = UIKit.colorScheme.text.secondary
        )
    }
}