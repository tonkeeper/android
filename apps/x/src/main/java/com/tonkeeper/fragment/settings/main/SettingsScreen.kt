package com.tonkeeper.fragment.settings.main

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.collection.ArrayMap
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeperx.R
import com.tonkeeper.core.widget.WidgetBalanceProvider
import com.tonkeeper.dialog.LogoutDialog
import com.tonkeeper.extensions.launch
import com.tonkeeper.fragment.currency.CurrencyScreen
import com.tonkeeper.fragment.main.MainTabScreen
import com.tonkeeper.fragment.root.RootActivity
import com.tonkeeper.fragment.settings.accounts.AccountsScreen
import com.tonkeeper.fragment.settings.language.LanguageFragment
import com.tonkeeper.fragment.settings.legal.LegalFragment
import com.tonkeeper.fragment.settings.list.SettingsAdapter
import com.tonkeeper.fragment.settings.list.item.SettingsIdItem
import com.tonkeeper.fragment.settings.main.popup.WalletVersionPopup
import com.tonkeeper.fragment.settings.security.SecurityFragment
import kotlinx.coroutines.launch
import ton.contract.WalletVersion
import uikit.decoration.ListCellDecoration
import uikit.extensions.verticalScrolled
import uikit.list.LinearLayoutManager
import uikit.navigation.Navigation.Companion.navigation
import uikit.popup.ActionSheet
import uikit.widget.HeaderView

class SettingsScreen: MainTabScreen<SettingsScreenState, SettingsScreenEffect, SettingsScreenFeature>(R.layout.fragment_settings) {

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
    private lateinit var listView: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)

        listView = view.findViewById(R.id.list)
        listView.layoutManager = LinearLayoutManager(view.context)
        listView.addItemDecoration(ListCellDecoration(view.context))
        listView.adapter = adapter
        listView.verticalScrolled.launch(this) {
            headerView.divider = it
        }
    }

    override fun newUiState(state: SettingsScreenState) {
        adapter.submitList(state.items)
    }

    override fun newUiEffect(effect: SettingsScreenEffect) {
        super.newUiEffect(effect)
        when (effect) {
            is SettingsScreenEffect.Logout -> {
                navigation?.initRoot(true)
            }
            is SettingsScreenEffect.SelectWalletVersion -> {
                pickWalletVersion(effect.view, effect.current, effect.wallets)
            }
            is SettingsScreenEffect.ReloadWallet -> {
                navigation?.initRoot(true)
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
            SettingsIdItem.MANAGE_WALLETS_ID -> {
                nav.add(AccountsScreen.newInstance())
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
            SettingsIdItem.CONTRACT_VERSION -> {
                feature.selectWalletVersion(view)
            }
            SettingsIdItem.LANGUAGE_ID -> { nav.add(LanguageFragment.newInstance()) }
        }
    }

    private fun pickWalletVersion(
        view: View,
        current: WalletVersion,
        wallets: ArrayMap<WalletVersion, String>
    ) {
        val actions = WalletVersionPopup(requireContext(), current, wallets)
        actions.doOnItemClick = { item ->
            val version = WalletVersion.valueOf(item.title.toString())
            feature.setWalletVersion(version)
        }
        actions.show(view)
    }

    override fun onUpScroll() {
        listView.scrollToPosition(0)
        headerView.divider = false
    }

    private fun installWidget() {
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