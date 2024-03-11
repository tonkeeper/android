package com.tonapps.tonkeeper.fragment.settings.main

import android.os.Build
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.App
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.tonkeeper.api.internal.repositories.KeysRepository
import com.tonapps.tonkeeper.core.currency.CurrencyUpdateWorker
import com.tonapps.tonkeeper.event.WalletSettingsEvent
import com.tonapps.tonkeeper.extensions.isRecoveryPhraseBackup
import com.tonapps.tonkeeper.extensions.label
import com.tonapps.tonkeeper.fragment.settings.list.item.SettingsAccountItem
import com.tonapps.tonkeeper.fragment.settings.list.item.SettingsIconItem
import com.tonapps.tonkeeper.fragment.settings.list.item.SettingsIdItem
import com.tonapps.tonkeeper.fragment.settings.list.item.SettingsItem
import com.tonapps.tonkeeper.fragment.settings.list.item.SettingsLogoItem
import com.tonapps.tonkeeper.fragment.settings.list.item.SettingsTextItem
import com.tonapps.uikit.list.ListCell
import core.EventBus
import uikit.mvi.UiFeature
import kotlinx.coroutines.launch

class SettingsScreenFeature: UiFeature<SettingsScreenState, SettingsScreenEffect>(SettingsScreenState()) {

    private companion object {
        private const val defaultSupportLink = "mailto:support@tonkeeper.com"
    }

    private val walletSettingsUpdate = fun(_: WalletSettingsEvent) {
        requestUpdateItems()
    }

    private val keysRepository = KeysRepository(App.instance)

    var supportLink = defaultSupportLink
    var tonkeeperNewsUrl = ""
    var directSupportUrl = ""

    init {
        requestUpdateItems()
        EventBus.subscribe(WalletSettingsEvent::class.java, walletSettingsUpdate)
    }

    private fun requestUpdateItems() {
        viewModelScope.launch {
            buildItems()
        }
    }

    private suspend fun loadConfig() {
        supportLink = keysRepository.getValue("support_link") ?: defaultSupportLink
        tonkeeperNewsUrl = keysRepository.getValue("tonkeeperNewsUrl") ?: ""
        directSupportUrl = keysRepository.getValue("directSupportUrl") ?: ""
    }

    fun logout() {
        viewModelScope.launch {
            App.walletManager.logout()
            CurrencyUpdateWorker.disable()
            sendEffect(SettingsScreenEffect.Logout)
        }
    }

    private suspend fun buildItems() {
        val wallet = App.walletManager.getWalletInfo() ?: return

        val items = mutableListOf<SettingsItem>()

        items.add(SettingsAccountItem(
            id = SettingsIdItem.ACCOUNT_ID,
            label = wallet.label,
            position = ListCell.Position.SINGLE,
            walletType = wallet.type
        ))

        items.add(SettingsTextItem(
            id = SettingsIdItem.CURRENCY_ID,
            titleRes = Localization.currency,
            data = App.settings.currency.code,
            position = ListCell.Position.FIRST
        ))

        items.add(SettingsIconItem(
            id = SettingsIdItem.SECURITY_ID,
            titleRes = Localization.security,
            iconRes = UIKitIcon.ic_lock_28,
            position = ListCell.Position.FIRST,
            dot = !wallet.isRecoveryPhraseBackup()
        ))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            items.add(SettingsIconItem(
                id = SettingsIdItem.WIDGET_ID,
                titleRes = Localization.widget,
                iconRes = R.drawable.ic_widget_28,
                position = ListCell.Position.MIDDLE,
            ))
        }
        items.add(SettingsIconItem(
            id = SettingsIdItem.THEME_ID,
            titleRes = Localization.theme,
            iconRes = UIKitIcon.ic_appearance_28,
            position = ListCell.Position.LAST,
        ))


        items.add(SettingsIconItem(
            id = SettingsIdItem.SUPPORT_ID,
            titleRes = Localization.support,
            iconRes = UIKitIcon.ic_message_bubble_28,
            position = ListCell.Position.FIRST,
        ))
        items.add(SettingsIconItem(
            id = SettingsIdItem.TONKEEPER_NEWS_ID,
            titleRes = Localization.tonkeeper_news,
            iconRes = R.drawable.ic_telegram_28,
            position = ListCell.Position.MIDDLE,
            secondaryIcon = true
        ))
        items.add(SettingsIconItem(
            id = SettingsIdItem.CONTACT_US_ID,
            titleRes = Localization.contact_us,
            iconRes = R.drawable.ic_envelope_28,
            position = ListCell.Position.MIDDLE,
            secondaryIcon = true
        ))
        items.add(SettingsIconItem(
            id = SettingsIdItem.LEGAL_ID,
            titleRes = Localization.legal,
            iconRes = R.drawable.ic_doc_28,
            position = ListCell.Position.LAST,
            secondaryIcon = true
        ))

        items.add(SettingsIconItem(
            id = SettingsIdItem.LOGOUT_ID,
            titleRes = Localization.log_out,
            iconRes = R.drawable.ic_door_28,
            position = ListCell.Position.SINGLE,
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
        EventBus.unsubscribe(WalletSettingsEvent::class.java, walletSettingsUpdate)
    }
}
