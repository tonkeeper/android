package com.tonkeeper.fragment.currency

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.R
import com.tonkeeper.fragment.currency.list.CurrencyAdapter
import com.tonkeeper.uikit.mvi.UiScreen
import com.tonkeeper.uikit.navigation.Navigation.Companion.nav
import com.tonkeeper.uikit.widget.BackHeaderView

class CurrencyScreen: UiScreen<CurrencyScreenState, CurrencyScreenFeature>(R.layout.fragment_currency) {

    companion object {
        fun newInstance() = CurrencyScreen()
    }

    override val viewModel: CurrencyScreenFeature by viewModels()

    private lateinit var listView: RecyclerView
    private lateinit var headerView: BackHeaderView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView = view.findViewById(R.id.list)
        headerView = view.findViewById(R.id.header)

        headerView.doOnBackClick = {
            nav()?.back()
        }
        headerView.bindContentPadding(listView)
    }

    override fun newUiState(state: CurrencyScreenState) {
        listView.adapter = CurrencyAdapter(state.items)
    }
}