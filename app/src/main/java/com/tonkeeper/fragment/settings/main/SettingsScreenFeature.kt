package com.tonkeeper.fragment.settings.main

import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.tonkeeperx.R
import com.tonkeeper.App
import com.tonkeeper.api.internal.repositories.KeysRepository
import com.tonkeeper.core.currency.CurrencyUpdateWorker
import com.tonkeeper.core.language.name
import com.tonkeeper.event.ChangeCurrencyEvent
import com.tonkeeper.event.WalletSettingsEvent
import com.tonkeeper.extensions.isRecoveryPhraseBackup
import com.tonkeeper.fragment.settings.list.item.SettingsIconItem
import com.tonkeeper.fragment.settings.list.item.SettingsIdItem
import com.tonkeeper.fragment.settings.list.item.SettingsItem
import com.tonkeeper.fragment.settings.list.item.SettingsLogoItem
import com.tonkeeper.fragment.settings.list.item.SettingsTextItem
import core.EventBus
import uikit.mvi.UiFeature
import kotlinx.coroutines.launch
import uikit.list.ListCell

class SettingsScreenFeature: UiFeature<SettingsScreenState, SettingsScreenEffect>(SettingsScreenState()) {

    private val changeCurrencyAction = fun(_: ChangeCurrencyEvent) {
        requestUpdateItems()
    }

    private val walletSettingsUpdate = fun(_: WalletSettingsEvent) {
        requestUpdateItems()
    }

    private val keysRepository = KeysRepository(App.instance)

    var supportLink = ""
    var tonkeeperNewsUrl = ""
    var directSupportUrl = ""

    init {
        requestUpdateItems()
        EventBus.subscribe(ChangeCurrencyEvent::class.java, changeCurrencyAction)
        EventBus.subscribe(WalletSettingsEvent::class.java, walletSettingsUpdate)
    }

    private fun requestUpdateItems() {
        viewModelScope.launch {
            buildItems()
        }
    }

    private suspend fun loadConfig() {
        supportLink = keysRepository.getValue("support_link") ?: ""
        tonkeeperNewsUrl = keysRepository.getValue("tonkeeperNewsUrl") ?: ""
        directSupportUrl = keysRepository.getValue("directSupportUrl") ?: ""
    }

    fun logout() {
        viewModelScope.launch {
            App.walletManager.logout()
            App.passcode.clearPinCode()
            CurrencyUpdateWorker.disable()
            sendEffect(SettingsScreenEffect.Logout)
        }
    }

    private suspend fun buildItems() {
        val wallet = App.walletManager.getWalletInfo() ?: return

        val items = mutableListOf<SettingsItem>()

        items.add(SettingsIconItem(
            id = SettingsIdItem.MANAGE_WALLETS_ID,
            titleRes = R.string.manage_wallets,
            iconRes = R.drawable.ic_gear_28,
            position = ListCell.Position.SINGLE,
            colorRes = uikit.R.color.accentBlue,
        ))

        items.add(SettingsTextItem(
            id = SettingsIdItem.CURRENCY_ID,
            titleRes = R.string.currency,
            data = App.settings.currency.code,
            position = ListCell.Position.FIRST
        ))
        items.add(SettingsTextItem(
            id = SettingsIdItem.LANGUAGE_ID,
            titleRes = R.string.language,
            data = App.settings.language.name,
            position = ListCell.Position.LAST
        ))

        items.add(SettingsIconItem(
            id = SettingsIdItem.SECURITY_ID,
            titleRes = R.string.security,
            iconRes = R.drawable.ic_lock_28,
            position = ListCell.Position.FIRST,
            colorRes = uikit.R.color.accentBlue,
            dot = !wallet.isRecoveryPhraseBackup()
        ))
        items.add(SettingsIconItem(
            id = SettingsIdItem.WIDGET_ID,
            titleRes = R.string.widget,
            iconRes = R.drawable.ic_widget_28,
            position = ListCell.Position.LAST,
            colorRes = uikit.R.color.accentBlue,
        ))

        items.add(SettingsIconItem(
            id = SettingsIdItem.SUPPORT_ID,
            titleRes = R.string.support,
            iconRes = R.drawable.ic_message_bubble_28,
            position = ListCell.Position.FIRST,
            colorRes = uikit.R.color.accentBlue,
        ))
        items.add(SettingsIconItem(
            id = SettingsIdItem.TONKEEPER_NEWS_ID,
            titleRes = R.string.tonkeeper_news,
            iconRes = R.drawable.ic_telegram_28,
            position = ListCell.Position.MIDDLE,
            colorRes = uikit.R.color.iconSecondary,
        ))
        items.add(SettingsIconItem(
            id = SettingsIdItem.CONTACT_US_ID,
            titleRes = R.string.contact_us,
            iconRes = R.drawable.ic_envelope_28,
            position = ListCell.Position.MIDDLE,
            colorRes = uikit.R.color.iconSecondary,
        ))
        items.add(SettingsIconItem(
            id = SettingsIdItem.LEGAL_ID,
            titleRes = R.string.legal,
            iconRes = R.drawable.ic_doc_28,
            position = ListCell.Position.LAST,
            colorRes = uikit.R.color.iconSecondary,
        ))

        items.add(SettingsIconItem(
            id = SettingsIdItem.LOGOUT_ID,
            titleRes = R.string.log_out,
            iconRes = R.drawable.ic_door_28,
            position = ListCell.Position.SINGLE,
            colorRes = uikit.R.color.accentBlue,
        ))

        items.add(getVersionItem())

        updateUiState {
            it.copy(
                items = items
            )
        }

        viewModelScope.launch {
            loadConfig()
        }
    }

    private fun getVersionItem(): SettingsLogoItem {
        return SettingsLogoItem(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
    }

    override fun onCleared() {
        super.onCleared()
        EventBus.unsubscribe(ChangeCurrencyEvent::class.java, changeCurrencyAction)
        EventBus.unsubscribe(WalletSettingsEvent::class.java, walletSettingsUpdate)
    }
}
