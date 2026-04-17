package com.tonapps.deposit.screens.network

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import com.tonapps.core.extensions.iconExternalUrl
import com.tonapps.deposit.components.VerticalAssetCell
import com.tonapps.mvi.props.observeSafeState
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonLargeItemSubtitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonDescriptionCell
import ui.components.moon.cell.MoonInfoCell
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.container.MoonScaffold
import ui.components.moon.container.MoonSurface

@Composable
fun SelectNetworkScreen(
    feature: SelectNetworkFeature,
    onSelect: (CryptoNetworkInfo) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    showFee: Boolean = false,
) {
    val state by feature.state.global.observeSafeState()

    when (val s = state) {
        SelectNetworkState.Loading -> MoonSurface { MoonLoaderCell() }
        SelectNetworkState.Empty -> onBack()
        is SelectNetworkState.Data -> {
            SelectNetworkContent(
                networks = s.networks,
                stablecoinCode = feature.data.selectedSymbol ?: feature.data.stablecoinCode,
                showFee = showFee,
                onSelect = onSelect,
                onBack = onBack,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun SelectNetworkContent(
    stablecoinCode: String,
    networks: List<CryptoNetworkInfo>,
    showFee: Boolean,
    onSelect: (CryptoNetworkInfo) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    MoonScaffold(
        Modifier
            .verticalScroll(rememberScrollState())
            .nestedScroll(rememberNestedScrollInteropConnection()),
        title = stringResource(Localization.choose_network_error, stablecoinCode),
        onClose = onClose,
        onBack = onBack,
    ) {
        MoonInfoCell(
            text = stringResource(Localization.deposit_network_warning)
        )

        Spacer(modifier = Modifier.height(16.dp))

        MoonBundleCell {
            Column {
                val context = LocalContext.current
                networks.fastForEachIndexed { index, networkInfo ->
                    val currency = networkInfo.currency
                    key(currency.code) {
                        val icon = remember {
                            networkInfo.networkImage
                                ?: currency.chain.iconExternalUrl(context)
                                ?: currency.iconUri?.toString()
                        }

                        if (index > 0) MoonItemDivider()

                        VerticalAssetCell(
                            name = currency.title.ifBlank { currency.chain.name },
                            assetImageUrl = icon,
                            extendedName = currency.chain.name,
                            onClick = { onSelect(networkInfo) },
                            content = if (showFee) {
                                {
                                    val fee = networkInfo.fee ?: "0"
                                    MoonLargeItemSubtitle(text = remember { "≈ $fee ${currency.symbol}" })
                                }
                            } else null
                        )
                    }
                }
            }
        }

        MoonDescriptionCell(
            text = stringResource(Localization.deposit_select_network_description, stablecoinCode)
        )
    }
}
