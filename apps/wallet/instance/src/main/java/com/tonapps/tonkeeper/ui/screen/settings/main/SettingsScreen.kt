package com.tonapps.tonkeeper.ui.screen.settings.main

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import com.tonapps.tonkeeper.core.widget.WidgetBalanceProvider
import com.tonapps.tonkeeper.fragment.root.RootActivity
import com.tonapps.tonkeeper.ui.screen.settings.currency.CurrencyScreen
import com.tonapps.tonkeeper.ui.screen.settings.language.LanguageScreen
import com.tonapps.tonkeeper.ui.screen.name.edit.EditNameScreen
import com.tonapps.tonkeeper.ui.screen.settings.main.list.Adapter
import com.tonapps.tonkeeper.ui.screen.settings.main.list.Item
import com.tonapps.tonkeeper.ui.screen.settings.security.SecurityScreen
import com.tonapps.tonkeeper.ui.screen.settings.theme.ThemeScreen
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
        when (item) {
            is Item.Currency -> navigation?.add(CurrencyScreen.newInstance())
            is Item.Language -> navigation?.add(LanguageScreen.newInstance())
            is Item.Account -> navigation?.add(EditNameScreen.newInstance())
            is Item.Theme -> navigation?.add(ThemeScreen.newInstance())
            is Item.Widget -> installWidget()
            is Item.Security -> navigation?.add(SecurityScreen.newInstance())
            else -> return
        }
    }

    private fun installWidget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val context = requireContext()
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val myProvider = ComponentName(context, WidgetBalanceProvider::class.java)
            if (!appWidgetManager.isRequestPinAppWidgetSupported) {
                return
            }
            val pinnedWidgetCallbackIntent = Intent(context, RootActivity::class.java)
            val successCallback = PendingIntent.getActivity(
                context, 0,
                pinnedWidgetCallbackIntent, PendingIntent.FLAG_IMMUTABLE
            )
            appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
        }
    }

    companion object {
        fun newInstance() = SettingsScreen()
    }
}