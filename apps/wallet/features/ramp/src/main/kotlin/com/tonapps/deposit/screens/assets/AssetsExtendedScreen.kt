package com.tonapps.deposit.screens.assets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.deposit.components.VerticalAssetCell
import com.tonapps.mvi.props.observeSafeState
import ui.components.moon.MoonItemDivider
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonSearchCell
import ui.components.moon.container.MoonScaffold
import ui.preview.ThemedPreview
import ui.workaround.hideKeyboardOnScrollConnection

@Composable
fun AssetsExtendedScreen(
    feature: AssetsCryptoExtendedFeature,
    title: String = "Crypto",
    onClose: () -> Unit,
    onBack: () -> Unit,
    onSelected: (WalletCurrency) -> Unit,
) {
    val state by feature.state.global.observeSafeState()
    Content(
        state = state,
        title = title,
        onSearchQuery = feature::setSearchQuery,
        onClose = onClose,
        onBack = onBack,
        onSelected = onSelected
    )
}

@Composable
private fun Content(
    state: DepositCryptoExtendedState,
    title: String,
    onSearchQuery: (String) -> Unit,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onSelected: (WalletCurrency) -> Unit,
) {
    val searchText = remember { mutableStateOf("") }

    MoonScaffold(
        title = title,
        onClose = onClose,
        onBack = onBack,
    ) {
        MoonSearchCell(
            searchText = searchText,
            onChanged = {
                searchText.value = it
                onSearchQuery(it)
            },
            error = false,
        )

        when (val state = state) {
            is DepositCryptoExtendedState.Data -> {
                val otherAssets = state.otherAssets
                if (otherAssets.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.nestedScroll(hideKeyboardOnScrollConnection()),
                        contentPadding = remember { PaddingValues(bottom = 32.dp) }
                    ) {
                        item {
                            MoonBundleCell {
                                Column {
                                    otherAssets.forEachIndexed { index, asset ->
                                        if (index > 0) MoonItemDivider()
                                        VerticalAssetCell(asset) { onSelected(asset) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ContentPreview() {
    ThemedPreview {
        Content(
            state = DepositCryptoExtendedState.Data(
                allAssets = listOf(),
                otherAssets = listOf(
                    WalletCurrency.TON,
                    WalletCurrency.TON,
                    WalletCurrency.TON,
                    WalletCurrency.USD,
                )
            ),
            title = "Title",
            onSearchQuery = {},
            onClose = {},
            onBack = {},
            onSelected = {},
        )
    }
}