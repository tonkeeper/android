package com.tonkeeper.fragment.currency

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.R
import com.tonkeeper.fragment.currency.list.CurrencyAdapter
import uikit.base.BaseFragment
import uikit.list.LinearLayoutManager
import uikit.mvi.UiScreen
import uikit.widget.BackHeaderView

class CurrencyScreen: UiScreen<CurrencyScreenState, CurrencyScreenEffect, CurrencyScreenFeature>(R.layout.fragment_currency), BaseFragment.SwipeBack {

    companion object {
        fun newInstance() = CurrencyScreen()
    }

    override val feature: CurrencyScreenFeature by viewModels()

    override var doOnDraggingProgress: ((Float) -> Unit)? = null
    override var doOnDragging: ((Boolean) -> Unit)? = null

    private val adapter = CurrencyAdapter {
        feature.setSelect(it.currency)
    }

    private lateinit var listView: RecyclerView
    private lateinit var headerView: BackHeaderView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnBackClick = { finish() }

        listView = view.findViewById(R.id.list)
        listView.layoutManager = LinearLayoutManager(view.context)
        listView.adapter = adapter
    }

    override fun newUiState(state: CurrencyScreenState) {
        adapter.submitList(state.items)
    }
}