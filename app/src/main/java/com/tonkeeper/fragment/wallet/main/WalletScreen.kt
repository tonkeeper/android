package com.tonkeeper.fragment.wallet.main

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.tonkeeper.R
import com.tonkeeper.fragment.receive.ReceiveScreen
import com.tonkeeper.fragment.send.SendScreen
import com.tonkeeper.fragment.wallet.main.pager.WalletScreenAdapter
import uikit.mvi.AsyncState
import uikit.mvi.UiScreen
import uikit.navigation.Navigation.Companion.nav
import uikit.widget.HeaderView
import uikit.widget.TabLayoutEx

class WalletScreen: UiScreen<WalletScreenState, WalletScreenEffect, WalletScreenFeature>(R.layout.fragment_wallet) {

    companion object {
        fun newInstance() = WalletScreen()
    }

    override val feature: WalletScreenFeature by viewModels()

    private lateinit var headerView: HeaderView
    private lateinit var amountView: AppCompatTextView
    private lateinit var addressView: AppCompatTextView
    private lateinit var sendButton: View
    private lateinit var receiveButton: View
    private lateinit var tabsContainerView: View
    private lateinit var tabsView: TabLayoutEx
    private lateinit var pagerView: ViewPager2

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)

        amountView = view.findViewById(R.id.amount)
        addressView = view.findViewById(R.id.address)

        sendButton = view.findViewById(R.id.send)
        sendButton.setOnClickListener {
            nav()?.add(SendScreen.newInstance())
        }
        receiveButton = view.findViewById(R.id.receive)
        receiveButton.setOnClickListener {
            nav()?.add(ReceiveScreen.newInstance())
        }

        tabsContainerView = view.findViewById(R.id.tabs_container)
        tabsView = view.findViewById(R.id.tabs)

        pagerView = view.findViewById(R.id.pager)
    }

    override fun newUiState(state: WalletScreenState) {
        setAsyncState(state.asyncState)
        amountView.text = state.displayBalance
        addressView.text = state.shortAddress
        pagerView.adapter = WalletScreenAdapter(state.pages)
        if (state.pages.size > 1) {
            tabsContainerView.visibility = View.VISIBLE
            TabLayoutMediator(tabsView, pagerView) { tab, position ->
                tab.setText(state.pages[position].titleRes)
            }.attach()
            tabsView.requestLayout()
        } else {
            tabsContainerView.visibility = View.GONE
        }
    }

    private fun setAsyncState(asyncState: AsyncState) {
        if (asyncState == AsyncState.Loading) {
            headerView.setUpdating(R.string.updating)
        } else {
            headerView.setDefault()
        }
    }
}