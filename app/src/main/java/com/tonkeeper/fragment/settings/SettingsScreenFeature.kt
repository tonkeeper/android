package com.tonkeeper.fragment.settings

import androidx.lifecycle.viewModelScope
import com.tonkeeper.BuildConfig
import com.tonkeeper.R
import com.tonkeeper.fragment.settings.list.item.SettingsCellItem
import com.tonkeeper.fragment.settings.list.item.SettingsItem
import com.tonkeeper.fragment.settings.list.item.SettingsLogoItem
import com.tonkeeper.ton.SupportedCurrency
import com.tonkeeper.uikit.list.BaseListItem
import com.tonkeeper.uikit.mvi.UiFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsScreenFeature: UiFeature<SettingsScreenState>(SettingsScreenState()) {

    init {
        buildItems()
    }

    private fun buildItems() {
        viewModelScope.launch {
            val items = mutableListOf<SettingsItem>()

            items.add(SettingsCellItem(
                id = SettingsCellItem.SECURITY_ID,
                titleRes = R.string.security,
                iconRes = R.drawable.ic_key_28,
                position = BaseListItem.Cell.Position.SINGLE
            ))

            items.add(SettingsCellItem(
                id = SettingsCellItem.CURRENCY_ID,
                titleRes = R.string.currency,
                right = SupportedCurrency.USD.code,
                position = BaseListItem.Cell.Position.SINGLE
            ))

            items.add(SettingsCellItem(
                id = SettingsCellItem.LOGOUT_ID,
                titleRes = R.string.log_out,
                iconRes = R.drawable.ic_door_28,
                position = BaseListItem.Cell.Position.SINGLE
            ))

            items.add(getVersionItem())

            updateUiState {
                it.copy(
                    items = items
                )
            }
        }
    }

    private suspend fun getVersionItem(): SettingsLogoItem = withContext(Dispatchers.IO) {
        SettingsLogoItem(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
    }
}
