package com.tonapps.deposit.screens.picker

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.deposit.components.AssetCell
import com.tonapps.mvi.props.observeSafeState
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemDivider
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.MoonSearchCell
import ui.components.moon.cell.defaultBundleType
import ui.components.moon.container.MoonScaffold
import ui.components.moon.container.MoonSurface
import ui.components.moon.screen.MoonEmptyScreen

@Composable
fun TokenPickerScreen(
    feature: TokenPickerFeature,
    selectedTokenAddress: String?,
    onTokenSelected: (AccountTokenEntity) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val state by feature.state.global.observeSafeState()

    when (val s = state) {
        is TokenPickerState.Loading -> MoonSurface { MoonLoaderCell() }
        is TokenPickerState.Data -> {
            TokenPickerContent(
                items = s.tokens,
                selectedTokenAddress = selectedTokenAddress,
                onTokenSelected = onTokenSelected,
                onBack = onBack,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun TokenPickerContent(
    items: List<TokenPickerItem>,
    selectedTokenAddress: String?,
    onTokenSelected: (AccountTokenEntity) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val searchText = remember { mutableStateOf("") }

    val filteredTokens by remember(items) {
        derivedStateOf {
            if (searchText.value.isBlank()) {
                items
            } else {
                items.filter {
                    it.token.symbol.contains(searchText.value, ignoreCase = true) ||
                            it.token.name.contains(searchText.value, ignoreCase = true)
                }
            }
        }
    }

    MoonScaffold(
        title = stringResource(Localization.choose_token),
        onClose = onClose,
        onBack = onBack,
    ) {
        MoonSearchCell(
            searchText = searchText,
            onChanged = { searchText.value = it },
            error = false,
        )
        Spacer(Modifier.height(8.dp))

        if (filteredTokens.isEmpty()) {
            MoonEmptyScreen(
                text = stringResource(Localization.cant_find_anything),
            )
        } else {
            LazyColumn(
                contentPadding = remember { PaddingValues(bottom = 32.dp) }
            ) {
                itemsIndexed(
                    items = filteredTokens,
                    key = { _, item -> item.token.address },
                    contentType = { _, item -> item::class }
                ) { index, item ->
                    if (index > 0) {
                        MoonItemDivider()
                    }

                    AssetCell(
                        position = defaultBundleType(filteredTokens.size, index),
                        entity = item.token.token,
                        description = item.balanceFormat,
                        isSelected = item.token.address == selectedTokenAddress,
                        onClick = {
                            onTokenSelected(item.token)
                            onBack()
                        },
                    )
                }
            }
        }
    }
}
