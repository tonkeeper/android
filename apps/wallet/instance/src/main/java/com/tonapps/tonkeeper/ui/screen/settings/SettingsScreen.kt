package com.tonapps.tonkeeper.ui.screen.settings

import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.ui.screen.currency.CurrencyScreen
import com.tonapps.tonkeeper.ui.screen.language.LanguageScreen
import com.tonapps.tonkeeper.ui.screen.name.edit.EditNameScreen
import com.tonapps.tonkeeper.ui.screen.settings.list.Adapter
import com.tonapps.tonkeeper.ui.screen.settings.list.Item
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.base.BaseListFragment
import uikit.extensions.collectFlow
import uikit.navigation.Navigation.Companion.navigation

class SettingsScreen: BaseListFragment(), BaseFragment.SwipeBack {

    private val settingsViewModel: SettingsViewModel by viewModel()

    private val adapter = Adapter(::onClickItem)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(getString(Localization.settings))
        setAdapter(adapter)

        collectFlow(settingsViewModel.uiItemsFlow, adapter::submitList)
    }

    private fun onClickItem(item: Item) {
        if (item is Item.Currency) {
            navigation?.add(CurrencyScreen.newInstance())
        } else if (item is Item.Language) {
            navigation?.add(LanguageScreen.newInstance())
        } else if (item is Item.Account) {
            navigation?.add(EditNameScreen.newInstance())
        }

    }

    companion object {
        fun newInstance() = SettingsScreen()
    }
}