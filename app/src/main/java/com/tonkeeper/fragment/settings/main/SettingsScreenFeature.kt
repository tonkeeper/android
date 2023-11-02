package com.tonkeeper.fragment.settings.main

import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.BuildConfig
import com.tonkeeper.R
import com.tonkeeper.core.currency.CurrencyUpdateWorker
import com.tonkeeper.event.ChangeCurrencyEvent
import com.tonkeeper.fragment.settings.list.item.SettingsIconItem
import com.tonkeeper.fragment.settings.list.item.SettingsIdItem
import com.tonkeeper.fragment.settings.list.item.SettingsItem
import com.tonkeeper.fragment.settings.list.item.SettingsLogoItem
import com.tonkeeper.fragment.settings.list.item.SettingsTextItem
import core.EventBus
import uikit.mvi.UiFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.list.ListCell

class SettingsScreenFeature: UiFeature<SettingsScreenState, SettingsScreenEffect>(SettingsScreenState()) {

    private val changeCurrencyAction = fun(_: ChangeCurrencyEvent) {
        buildItems()
    }

    init {
        buildItems()
        EventBus.subscribe(ChangeCurrencyEvent::class.java, changeCurrencyAction)
    }

    fun logout() {
        viewModelScope.launch {
            App.walletManager.clear()
            CurrencyUpdateWorker.disable()
            sendEffect(SettingsScreenEffect.Logout)
        }
    }

    private fun buildItems() {
        viewModelScope.launch {
            val items = mutableListOf<SettingsItem>()

            items.add(SettingsIconItem(
                id = SettingsIdItem.SECURITY_ID,
                titleRes = R.string.security,
                iconRes = R.drawable.ic_key_28,
                position = ListCell.Position.SINGLE
            ))

            items.add(SettingsTextItem(
                id = SettingsIdItem.CURRENCY_ID,
                titleRes = R.string.currency,
                data = App.settings.currency.code,
                position = ListCell.Position.SINGLE
            ))

            items.add(SettingsIconItem(
                id = SettingsIdItem.LEGAL_ID,
                titleRes = R.string.legal,
                iconRes = R.drawable.ic_doc_28,
                position = ListCell.Position.SINGLE
            ))

            items.add(SettingsIconItem(
                id = SettingsIdItem.LOGOUT_ID,
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
