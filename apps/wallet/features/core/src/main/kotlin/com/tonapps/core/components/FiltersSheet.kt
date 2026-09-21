package com.tonapps.core.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonSwitch
import ui.components.moon.MoonTopAppBar
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.preview.ThemedPreview

data class FilterSwitchItem(
    val title: String,
    val subtitle: String,
    val checked: Boolean,
    val onToggle: () -> Unit,
)

@Composable
fun FiltersSheet(
    item: FilterSwitchItem,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator { onClose() }
    MoonModalDialog(navigator = navigator) {
        MoonTopAppBar(
            title = stringResource(Localization.filters),
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { navigator.close() },
            backgroundColor = Color.Transparent,
        )

        MoonBundleCell {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = item.onToggle)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    MoonItemTitle(text = item.title, maxLines = 2)
                    MoonItemSubtitle(text = item.subtitle)
                }
                MoonSwitch(
                    checked = item.checked,
                    onCheckedChange = item.onToggle,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Preview
@Composable
private fun FiltersSheetPreview() {
    ThemedPreview {
        FiltersSheet(
            item = FilterSwitchItem(
                title = stringResource(Localization.hide_tiny_transfers),
                subtitle = stringResource(Localization.hide_tiny_transfers_desc),
                checked = true,
                onToggle = {},
            ),
            onClose = {},
        )
    }
}
