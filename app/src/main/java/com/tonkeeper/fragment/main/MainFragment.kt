package com.tonkeeper.fragment.main

import android.os.Bundle
import android.view.View
import com.tonkeeper.R
import com.tonkeeper.fragment.wallet.history.HistoryScreen
import com.tonkeeper.fragment.settings.SettingsScreen
import com.tonkeeper.fragment.wallet.main.WalletScreen
import com.tonkeeper.uikit.base.fragment.BaseFragment
import com.tonkeeper.uikit.widget.BottomTabsView

class MainFragment: BaseFragment(R.layout.fragment_main) {
    companion object {
        fun newInstance() = MainFragment()
    }

    private val walletScreen = WalletScreen.newInstance()
    private val historyScreen = HistoryScreen.newInstance()
    private val settingsScreen = SettingsScreen.newInstance()

    private lateinit var bottomTabsView: BottomTabsView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bottomTabsView = view.findViewById(R.id.bottom_tabs)
        bottomTabsView.doOnClick = { _, itemId ->
            setFragment(itemId)
        }
        setFragment(R.id.wallet)
    }

    private fun fragmentByItemId(itemId: Int) = when (itemId) {
        R.id.wallet -> walletScreen
        R.id.activity -> historyScreen
        R.id.settings -> settingsScreen
        else -> throw IllegalArgumentException("Unknown itemId: $itemId")
    }

    private fun setFragment(itemId: Int) {
        val fragmentTransaction = childFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.child_fragment, fragmentByItemId(itemId))
        fragmentTransaction.commit()
    }
}