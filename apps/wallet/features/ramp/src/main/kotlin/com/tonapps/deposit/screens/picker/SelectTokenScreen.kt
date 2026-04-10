package com.tonapps.deposit.screens.picker

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.deposit.components.AssetCell
import com.tonapps.icu.CurrencyFormatter
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
                tokens = s.tokens,
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
    tokens: List<AccountTokenEntity>,
    selectedTokenAddress: String?,
    onTokenSelected: (AccountTokenEntity) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val searchText = remember { mutableStateOf("") }

    val filteredTokens by remember(tokens) {
        derivedStateOf {
            if (searchText.value.isBlank()) {
                tokens
            } else {
                tokens.filter {
                    it.symbol.contains(searchText.value, ignoreCase = true) ||
                            it.name.contains(searchText.value, ignoreCase = true)
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
                    key = { _, token -> token.address },
                    contentType = { _, token -> token::class }
                ) { index, token ->
                    if (index > 0) MoonItemDivider()

                    val balance = remember(token.token) {
                        CurrencyFormatter.format(token.symbol.take(6), token.balance.uiBalance)
                            .toString()
                    }

                    AssetCell(
                        position = defaultBundleType(filteredTokens.size, index),
                        entity = token.token,
                        description = balance,
                        isSelected = token.address == selectedTokenAddress,
                        onClick = {
                            onTokenSelected(token)
                            onBack()
                        },
                    )
                }
            }
        }
    }
}
