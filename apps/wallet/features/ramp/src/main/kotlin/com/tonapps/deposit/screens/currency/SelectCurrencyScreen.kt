package com.tonapps.deposit.screens.currency

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.deposit.components.VerticalAssetCell
import com.tonapps.mvi.props.observeSafeState
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemDivider
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.MoonSearchCell
import ui.components.moon.cell.defaultBundleType
import ui.components.moon.container.MoonScaffold
import ui.components.moon.container.MoonSurface
import ui.components.moon.screen.MoonEmptyScreen
import ui.workaround.hideKeyboardOnScrollConnection

@Composable
fun SelectCurrencyScreen(
    feature: SelectCurrencyFeature,
    selectedCurrencyCode: String?,
    onConfirm: (WalletCurrency) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val state by feature.state.global.observeSafeState()

    when (val s = state) {
        is SelectCurrencyState.Loading -> MoonSurface { MoonLoaderCell() }
        is SelectCurrencyState.Empty -> MoonEmptyScreen(
            text = stringResource(Localization.cant_find_anything),
        )

        is SelectCurrencyState.Data -> {
            SelectCurrencyContent(
                currencies = s.currencies,
                selectedCurrencyCode = selectedCurrencyCode,
                onConfirm = onConfirm,
                onBack = onBack,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun SelectCurrencyContent(
    currencies: List<WalletCurrency>,
    selectedCurrencyCode: String?,
    onConfirm: (WalletCurrency) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val searchText = remember { mutableStateOf("") }
    val filteredCurrencies: List<WalletCurrency> by remember(currencies) {
        derivedStateOf {
            val query = searchText.value.trim()
            if (query.isEmpty()) {
                currencies
            } else {
                currencies.filter { it.containsQuery(query) }
            }
        }
    }

    MoonScaffold(
        title = stringResource(Localization.choose_currency),
        onClose = onClose,
        onBack = onBack,
    ) {
        MoonSearchCell(
            searchText = searchText,
            onChanged = { searchText.value = it },
            isFocusOnStart = true,
            error = false,
        )

        if (filteredCurrencies.isEmpty()) {
            MoonEmptyScreen(
                text = stringResource(Localization.cant_find_anything),
            )
        } else {
            LazyColumn(
                modifier = Modifier.nestedScroll(hideKeyboardOnScrollConnection()),
                contentPadding = remember { PaddingValues(bottom = 32.dp) }
            ) {
                itemsIndexed(
                    items = filteredCurrencies,
                    key = { _, currency -> currency.key },
                    contentType = { _, currency -> currency::class }
                ) { index, currency ->
                    MoonBundleCell(
                        position = defaultBundleType(filteredCurrencies.size, index)
                    ) {
                        if (index > 0) MoonItemDivider()
                        val isChecked = currency.code == selectedCurrencyCode
                        VerticalAssetCell(
                            currency = currency,
                            isSelected = isChecked,
                            onClick = {
                                onConfirm(currency)
                                onBack()
                            }
                        )
                    }
                }
            }
        }
    }
}

