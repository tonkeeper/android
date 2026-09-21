package com.tonapps.perps.screens.deposit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tonapps.perps.data.PerpsPaymentMethod
import com.tonapps.perps.data.formatTokenAmount
import com.tonapps.perps.data.formatUsd
import com.tonapps.uikit.icon.UIKitIcon
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundleCellContent
import ui.components.moon.cell.TextCheckCell
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.painterResource
import ui.theme.UIKit

@Composable
fun PerpsPaymentMethodDialog(
    methods: List<PerpsPaymentMethod>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)

    MoonModalDialog(navigator = navigator) {
        MoonTopAppBarSimple(
            title = "Payment method",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { navigator.close() },
        )

        MoonSurface {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                MoonBundleCell(contentPadding = MoonBundleCellContent.Default) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoonItemSubtitle(
                            modifier = Modifier.weight(1f),
                            text = "Credited to your USD account. Tokens are automatically converted to USD.",
                            color = UIKit.colorScheme.text.secondary,
                        )
                        Spacer(Modifier.size(8.dp))
                        MoonItemIcon(
                            painter = painterResource(UIKitIcon.ic_information_circle_16),
                            color = UIKit.colorScheme.icon.secondary,
                        )
                    }
                }

                MoonBundleCell {
                    Column {
                        methods.forEachIndexed { index, method ->
                            if (index > 0) {
                                MoonItemDivider()
                            }
                            TextCheckCell(
                                title = method.symbol,
                                subtitle = "${formatTokenAmount(method.balance, method.symbol)} · ${formatUsd(method.balance * method.usdPrice)}",
                                tags = { PerpsFeeBadge(method) },
                                isChecked = method.id == selectedId,
                                onCheckedChange = {
                                    navigator.close()
                                    onSelect(method.id)
                                },
                                minHeight = 76.dp,
                                image = {
                                    MoonItemImage(
                                        painter = painterResource(method.iconRes),
                                        size = 44.dp,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
