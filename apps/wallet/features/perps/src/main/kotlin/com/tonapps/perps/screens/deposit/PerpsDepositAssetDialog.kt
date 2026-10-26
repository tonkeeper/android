package com.tonapps.perps.screens.deposit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.TextCheckCell
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.theme.UIKit

@Composable
fun PerpsDepositAssetDialog(
    sources: List<PerpsDepositSourceUi>,
    selectedAssetId: String?,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)

    MoonModalDialog(navigator = navigator) {
        MoonTopAppBarSimple(
            title = stringResource(Localization.choose_asset),
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { navigator.close() },
        )

        MoonSurface {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MoonItemSubtitle(
                    text = stringResource(Localization.perps_deposit_converted_note),
                    color = UIKit.colorScheme.text.secondary,
                )

                Spacer(Modifier.height(16.dp))

                MoonBundleCell {
                    Column {
                        sources.forEachIndexed { index, item ->
                            val source = item.source
                            if (index > 0) {
                                MoonItemDivider()
                            }
                            TextCheckCell(
                                title = source.symbol,
                                subtitle = sourceSubtitle(item),
                                tags = { SourceTags(source) },
                                isChecked = source.assetId == selectedAssetId,
                                onCheckedChange = {
                                    navigator.close()
                                    onSelect(source.assetId)
                                },
                                minHeight = 76.dp,
                                image = { SourceImage(source) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
