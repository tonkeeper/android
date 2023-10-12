package com.tonkeeper.fragment.settings

import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.BuildConfig
import com.tonkeeper.R
import com.tonkeeper.event.ChangeCurrencyEvent
import com.tonkeeper.fragment.settings.list.item.SettingsCellItem
import com.tonkeeper.fragment.settings.list.item.SettingsItem
import com.tonkeeper.fragment.settings.list.item.SettingsLogoItem
import core.EventBus
import uikit.mvi.UiFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.list.ListCell

class SettingsScreenFeature: UiFeature<SettingsScreenState>(SettingsScreenState()) {

    private val changeCurrencyAction = fun(_: ChangeCurrencyEvent) {
        buildItems()
    }

    init {
        buildItems()
        EventBus.subscribe(ChangeCurrencyEvent::class.java, changeCurrencyAction)
    }

    private fun buildItems() {
        viewModelScope.launch {
            val items = mutableListOf<SettingsItem>()

            items.add(SettingsCellItem(
                id = SettingsCellItem.SECURITY_ID,
                titleRes = R.string.security,
                iconRes = R.drawable.ic_key_28,
                position = ListCell.Position.SINGLE
            ))

            items.add(SettingsCellItem(
                id = SettingsCellItem.CURRENCY_ID,
                titleRes = R.string.currency,
                right = App.settingsManager.currency.code,
                position = ListCell.Position.SINGLE
            ))

            items.add(SettingsCellItem(
                id = SettingsCellItem.LEGAL_ID,
                titleRes = R.string.legal,
                iconRes = R.drawable.ic_doc_28,
                position = ListCell.Position.SINGLE
            ))

            items.add(SettingsCellItem(
                id = SettingsCellItem.LOGOUT_ID,
                titleRes = R.string.log_out,
                iconRes = R.drawable.ic_door_28,
                position = ListCell.Position.SINGLE
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

    override fun onCleared() {
        super.onCleared()
        EventBus.unsubscribe(ChangeCurrencyEvent::class.java, changeCurrencyAction)
    }
}
