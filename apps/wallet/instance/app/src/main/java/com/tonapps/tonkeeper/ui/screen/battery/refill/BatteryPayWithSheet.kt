package com.tonapps.tonkeeper.ui.screen.battery.refill

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonChevronRight
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.TextCell
import ui.components.moon.dialog.DialogNavigator
import ui.components.moon.dialog.MoonModalDialog

@Composable
fun BatteryPayWithSheet(
    navigator: DialogNavigator,
    methods: List<PayMethodRowUi>,
    enabled: Boolean,
    onSelect: (PayMethodRowUi) -> Unit,
) {
    MoonModalDialog(navigator = navigator) {
        BatteryPayWithList(
            methods = methods,
            enabled = enabled,
            onClose = { navigator.close() },
            onSelect = { row ->
                navigator.close()
                onSelect(row)
            },
        )
    }
}

@Composable
internal fun BatteryPayWithList(
    methods: List<PayMethodRowUi>,
    enabled: Boolean,
    onClose: () -> Unit,
    onSelect: (PayMethodRowUi) -> Unit,
) {
    MoonTopAppBarSimple(
        title = stringResource(Localization.battery_pay_with_title),
        actionIconRes = UIKitIcon.ic_close_16,
        onActionClick = onClose,
    )
    LazyColumn(
        contentPadding = remember { PaddingValues(bottom = 16.dp) },
    ) {
        itemsIndexed(methods, key = { _, row -> row.id }) { index, row ->
            MoonBundleCell(position = MoonBundlePosition.default(methods.size, index)) {
                TextCell(
                    modifier = Modifier.dimmedUnless(enabled),
                    title = row.title,
                    subtitle = row.caption,
                    image = {
                        PayMethodIcon(
                            imageUrl = row.imageUrl,
                            chainBadge = row.chainBadge,
                            isStore = row.kind == PayMethodKind.Store,
                        )
                    },
                    content = {
                        if (row.kind == PayMethodKind.Asset) {
                            MoonChevronRight()
                        } else if (row.selected) {
                            SelectedMark()
                        }
                    },
                    onClick = { onSelect(row) }.takeIf { enabled },
                    minHeight = ROW_HEIGHT,
                )
            }
        }
    }
}
