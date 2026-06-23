package com.tonapps.deposit.screens.provider

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.icu.Coins
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonDescriptionCell
import ui.components.moon.cell.TextCheckCell
import ui.components.moon.container.MoonSurface
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.theme.UIKit

@Composable
fun SelectProviderDialog(
    providers: List<ProviderWithQuote>,
    selectedProviderId: String?,
    currencyCode: String,
    onConfirm: (providerId: String, minAmount: Coins?) -> Unit,
    onClose: () -> Unit,
) {
    var localSelectedId by remember { mutableStateOf(selectedProviderId) }
    val navigator = rememberDialogNavigator(onClose = onClose)

    MoonModalDialog(navigator = navigator) {
        MoonTopAppBarSimple(
            title = stringResource(Localization.provider),
            navigationIconRes = UIKitIcon.ic_chevron_down_16,
            onNavigationClick = { navigator.close() },
        )

        MoonSurface {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                MoonBundleCell {
                    Column {
                        val sorted = remember(providers) {
                            providers.sortedByDescending { it.quote != null }
                        }
                        sorted.forEachIndexed { index, entry ->
                            if (index > 0) MoonItemDivider()
                            val isAvailable = entry.quote != null
                            val isChecked = isAvailable && entry.info.id == localSelectedId

                            TextCheckCell(
                                title = entry.info.title,
                                subtitle = entry.rate?.rateFormatted,
                                subtitleColor = if (isAvailable) UIKit.colorScheme.text.secondary else UIKit.colorScheme.text.tertiary,
                                description = if (!isAvailable) {
                                    when {
                                        entry.info.minAmount.isPositive -> {
                                            {
                                                MoonItemSubtitle(
                                                    text = stringResource(Localization.deposit_min_amount_with_currency, entry.info.minAmount.value, currencyCode),
                                                    color = UIKit.colorScheme.accent.orange,
                                                )
                                            }
                                        }
                                        entry.info.maxAmount != null -> {
                                            {
                                                MoonItemSubtitle(
                                                    text = stringResource(Localization.deposit_max_amount_with_currency, entry.info.maxAmount.value, currencyCode),
                                                    color = UIKit.colorScheme.accent.orange,
                                                )
                                            }
                                        }
                                        else -> null
                                    }
                                } else {
                                    null
                                },
                                tags = {
                                    if (entry.info.isBest) {
                                        MoonLabel(
                                            stringResource(Localization.best),
                                            colors = MoonLabelDefault.blue(),
                                        )
                                    }
                                },
                                isChecked = isChecked,
                                onCheckedChange = {
                                    navigator.close()
                                    if (isAvailable) {
                                        onConfirm(entry.info.id, null)
                                    } else {
                                        val limitAmount = when {
                                            entry.info.minAmount.isPositive -> entry.info.minAmount
                                            entry.info.maxAmount != null -> entry.info.maxAmount
                                            else -> null
                                        }
                                        onConfirm(entry.info.id, limitAmount)
                                    }
                                },
                                minHeight = 76.dp,
                                image = {
                                    MoonItemImage(
                                        shape = UIKit.shapes.large,
                                        image = entry.info.imageUrl,
                                        size = 44.dp,
                                    )
                                },
                            )
                        }
                    }
                }

                MoonDescriptionCell(
                    text = stringResource(Localization.deposit_other_providers_hint)
                )
            }
        }
    }
}
