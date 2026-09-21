package com.tonapps.core.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.exchange.SwapSlippage
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemTitle
import ui.theme.UIKit

/**
 * Tappable slippage value that opens a dropdown of the configured options. Renders only the
 * value + menu, so each screen supplies its own surrounding cell/title (the swap input card,
 * the confirm property list, …).
 */
@Composable
fun SlippageSelector(
    slippage: SwapSlippage,
    selectedBps: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentBps = selectedBps ?: slippage.defaultBps
    val currentLabel = slippage.options.firstOrNull { it.bps == currentBps }?.formatted.orEmpty()

    Box {
        Row(
            modifier = modifier.clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoonItemTitle(text = currentLabel)

            Spacer(Modifier.width(4.dp))

            MoonItemIcon(
                painter = painterResource(UIKitIcon.ic_switch_16),
                color = UIKit.colorScheme.icon.secondary,
            )
        }

        DropdownMenu(
            containerColor = UIKit.colorScheme.background.contentTint,
            shape = UIKit.shapes.large,
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Column {
                slippage.options.forEach { option ->
                    SlippageItemCell(
                        title = option.formatted,
                        isChecked = option.bps == currentBps,
                        onClick = {
                            expanded = false
                            onSelect(option.bps)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SlippageItemCell(
    title: String,
    isChecked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoonItemTitle(
            modifier = Modifier.defaultMinSize(minWidth = 56.dp),
            text = title,
        )

        MoonItemIcon(
            modifier = Modifier.alpha(if (isChecked) 1f else 0f),
            painter = painterResource(UIKitIcon.ic_done_16),
            color = UIKit.colorScheme.accent.blue,
        )
    }
}
