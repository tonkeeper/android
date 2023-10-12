package com.tonkeeper.fragment.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.fragment.currency.CurrencyScreen
import com.tonkeeper.fragment.legal.LegalFragment
import com.tonkeeper.fragment.settings.list.SettingsAdapter
import com.tonkeeper.fragment.settings.list.SettingsItemDecoration
import com.tonkeeper.fragment.settings.list.item.SettingsCellItem
import kotlinx.coroutines.launch
import uikit.mvi.UiScreen
import uikit.navigation.Navigation.Companion.nav

class SettingsScreen: UiScreen<SettingsScreenState, SettingsScreenFeature>(R.layout.fragment_settings) {

    companion object {
        fun newInstance() = SettingsScreen()
    }

    override val viewModel: SettingsScreenFeature by viewModels()

    private lateinit var listView: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView = view.findViewById(R.id.list)
        listView.addItemDecoration(SettingsItemDecoration(view.context))
    }

    override fun newUiState(state: SettingsScreenState) {
        listView.adapter = SettingsAdapter(state.items) { item ->
            if (item is SettingsCellItem) {
                onCellClick(item)
            }
        }
    }

    private fun onCellClick(item: SettingsCellItem) {
        val nav = nav() ?: return

        when (item.id) {
            SettingsCellItem.LOGOUT_ID -> {
                lifecycleScope.launch {
                    App.walletManager.clear()
                    nav.init()
                }
            }
            SettingsCellItem.CURRENCY_ID -> {
                nav.add(CurrencyScreen.newInstance())
            }
            SettingsCellItem.LEGAL_ID -> {
                nav.add(LegalFragment.newInstance())
            }
        }
    }
}