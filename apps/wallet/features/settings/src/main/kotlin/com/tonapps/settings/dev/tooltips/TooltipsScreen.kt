package com.tonapps.settings.dev.tooltips

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tonapps.core.flags.TooltipManager
import com.tonapps.core.flags.TooltipState
import com.tonapps.core.flags.WalletTooltipKey
import ui.components.moon.MoonItemTitle
import ui.components.moon.cell.TextCell
import ui.components.moon.container.MoonScaffold
import ui.theme.UIKit

@Composable
fun TooltipsScreen(
    onBack: () -> Unit,
) {
    MoonScaffold(
        title = "Tooltips",
        onBack = onBack,
        content = {
            Column {
                val keys = remember { WalletTooltipKey.entries }
                for (i in keys) {
                    var state by remember { mutableStateOf(TooltipManager.getState(i)) }
                    var expanded by remember { mutableStateOf(false) }

                    TextCell(
                        title = i.tooltipName,
                        content = {
                            Box {
                                MoonItemTitle(
                                    text = state.name,
                                    color = UIKit.colorScheme.text.secondary,
                                )
                                DropdownMenu(
                                    containerColor = UIKit.colorScheme.background.contentTint,
                                    shape = UIKit.shapes.large,
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                ) {
                                    TooltipState.entries.forEach { option ->
                                        TextCell(
                                            title = option.name,
                                            titleColor = if (option == state) {
                                                UIKit.colorScheme.text.accent
                                            } else {
                                                UIKit.colorScheme.text.primary
                                            },
                                            onClick = {
                                                state = option
                                                TooltipManager.setState(i, option)
                                                expanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        },
                        onClick = {
                            expanded = true
                        },
                    )
                }
            }
        },
    )
}
