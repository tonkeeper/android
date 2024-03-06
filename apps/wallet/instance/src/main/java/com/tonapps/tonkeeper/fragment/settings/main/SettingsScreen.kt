package com.tonapps.tonkeeper.fragment.settings.main

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.core.widget.WidgetBalanceProvider
import com.tonapps.tonkeeper.dialog.LogoutDialog
import com.tonapps.tonkeeper.fragment.root.RootActivity
import com.tonapps.tonkeeper.fragment.settings.language.LanguageFragment
import com.tonapps.tonkeeper.fragment.settings.legal.LegalFragment
import com.tonapps.tonkeeper.fragment.settings.list.SettingsAdapter
import com.tonapps.tonkeeper.fragment.settings.list.item.SettingsIdItem
import com.tonapps.tonkeeper.fragment.settings.security.SecurityFragment
import com.tonapps.tonkeeper.ui.screen.currency.CurrencyScreen
import com.tonapps.tonkeeper.ui.screen.theme.ThemeScreen
import uikit.base.BaseFragment
import uikit.decoration.ListCellDecoration
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.collectFlow
import uikit.extensions.topScrolled
import uikit.mvi.UiScreen
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView
import uikit.widget.SimpleRecyclerView

class SettingsScreen: UiScreen<SettingsScreenState, SettingsScreenEffect, SettingsScreenFeature>(R.layout.fragment_settings), BaseFragment.SwipeBack {

    companion object {
        fun newInstance() = SettingsScreen()
    }

    override val feature: SettingsScreenFeature by viewModels()

    private val logoutDialog: LogoutDialog by lazy {
        LogoutDialog(requireContext())
    }

    private val adapter = SettingsAdapter { item, view ->
        if (item is SettingsIdItem) {
            onCellClick(item, view)
        }
    }

    private lateinit var headerView: HeaderView
    private lateinit var listView: SimpleRecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        listView = view.findViewById(R.id.list)
        listView.addItemDecoration(ListCellDecoration(view.context))
        listView.adapter = adapter
        listView.applyNavBottomPadding()
        collectFlow(listView.topScrolled, headerView::setDivider)
    }

    override fun newUiState(state: SettingsScreenState) {
        adapter.submitList(state.items)
    }

    override fun newUiEffect(effect: SettingsScreenEffect) {
        super.newUiEffect(effect)
        when (effect) {
            is SettingsScreenEffect.Logout -> {
                // navigation?.initRoot(true)
            }
            is SettingsScreenEffect.ReloadWallet -> {
                // navigation?.initRoot(true)
            }
        }
    }

    private fun showLogoutDialog() {
        logoutDialog.show {
            feature.logout()
        }
    }

    private fun onCellClick(item: SettingsIdItem, view: View) {
        val nav = navigation ?: return

        when (item.id) {
            SettingsIdItem.ACCOUNT_ID -> {
                // nav.add(RenameFragment.newInstance())
            }
            SettingsIdItem.LOGOUT_ID -> {
                showLogoutDialog()
            }
            SettingsIdItem.CURRENCY_ID -> {
                nav.add(CurrencyScreen.newInstance())
            }
            SettingsIdItem.LEGAL_ID -> {
                nav.add(LegalFragment.newInstance())
            }
            SettingsIdItem.SECURITY_ID -> {
                nav.add(SecurityFragment.newInstance())
            }
            SettingsIdItem.CONTACT_US_ID -> {
                nav.openURL(feature.supportLink, true)
            }
            SettingsIdItem.TONKEEPER_NEWS_ID -> {
                nav.openURL(feature.tonkeeperNewsUrl, true)
            }
            SettingsIdItem.SUPPORT_ID -> {
                nav.openURL(feature.directSupportUrl, true)
            }
            SettingsIdItem.WIDGET_ID -> {
                installWidget()
            }
            SettingsIdItem.THEME_ID -> {
                nav.add(ThemeScreen.newInstance())
            }
            SettingsIdItem.LANGUAGE_ID -> { nav.add(LanguageFragment.newInstance()) }
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

}