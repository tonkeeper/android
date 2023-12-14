package com.tonkeeper.fragment.settings.accounts

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.R
import com.tonkeeper.fragment.settings.accounts.list.AccountsAdapter
import uikit.base.BaseFragment
import uikit.decoration.ListCellDecoration
import uikit.list.LinearLayoutManager
import uikit.mvi.UiScreen
import uikit.widget.HeaderView

class AccountsScreen: UiScreen<AccountsScreenState, AccountsScreenEffect, AccountsScreenFeature>(R.layout.fragment_accounts), BaseFragment.SwipeBack {

    companion object {
        fun newInstance() = AccountsScreen()
    }

    override val feature: AccountsScreenFeature by viewModels()

    override var doOnDragging: ((Boolean) -> Unit)? = null
    override var doOnDraggingProgress: ((Float) -> Unit)? = null

    private val adapter = AccountsAdapter()

    private lateinit var headerView: HeaderView
    private lateinit var listView: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { finish() }

        listView = view.findViewById(R.id.list)
        listView.adapter = adapter
        listView.layoutManager = LinearLayoutManager(view.context)
        listView.addItemDecoration(ListCellDecoration(view.context))
    }

    override fun newUiState(state: AccountsScreenState) {
        adapter.submitList(state.getItems())
    }

}