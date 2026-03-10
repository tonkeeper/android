package com.tonapps.tonkeeper.ui.screen.events.compose.details.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonapps.tonkeeper.ui.screen.events.compose.details.TxDetailsViewModel
import ui.components.ActionButtonIcon
import ui.components.popup.ActionMenu
import ui.painterResource

@Composable
fun TxActionsMenu(
    viewModel: TxDetailsViewModel
) {
    val items by viewModel.uiActionItemsFlow.collectAsStateWithLifecycle(emptyList())
    var expanded by remember { mutableStateOf(false) }

    Box {
        ActionButtonIcon(
            painter = painterResource(com.tonapps.tonkeeperx.R.drawable.ic_ellipsis_16),
            onClick = {
                expanded = !expanded
            },
        )

        ActionMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = items,
            onItemClick = { item ->
                expanded = false
                viewModel.onClickActionMenuItem(item.id)
            }
        )
    }
}