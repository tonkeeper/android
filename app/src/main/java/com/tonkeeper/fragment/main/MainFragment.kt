package com.tonkeeper.fragment.main

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.core.currency.CurrencyUpdateWorker
import com.tonkeeper.core.widget.Widget
import com.tonkeeper.core.widget.WidgetBalanceProvider
import com.tonkeeper.core.widget.WidgetRateProvider
import com.tonkeeper.fragment.wallet.history.HistoryScreen
import com.tonkeeper.fragment.settings.main.SettingsScreen
import com.tonkeeper.fragment.wallet.main.WalletScreen
import kotlinx.coroutines.launch
import uikit.base.fragment.BaseFragment
import uikit.widget.BottomTabsView

class MainFragment: BaseFragment(R.layout.fragment_main) {

    companion object {
        fun newInstance() = MainFragment()
    }

    private val fragmentMap by lazy {
        mapOf(
            R.id.wallet to WalletScreen.newInstance(),
            R.id.activity to HistoryScreen.newInstance(),
            R.id.settings to SettingsScreen.newInstance()
        )
    }

    private var currentFragment: BaseFragment? = null

    private lateinit var bottomTabsView: BottomTabsView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CurrencyUpdateWorker.enable()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bottomTabsView = view.findViewById(R.id.bottom_tabs)
        bottomTabsView.doOnClick = { _, itemId ->
            setFragment(itemId)
        }
        setFragment(R.id.wallet)
    }

    private fun fragmentByItemId(itemId: Int): BaseFragment {
        return fragmentMap[itemId] ?: throw IllegalArgumentException("Unknown itemId: $itemId")
    }

    private fun setFragment(itemId: Int) {
        val tag = itemId.toString()
        val isAlreadyFragment = childFragmentManager.findFragmentByTag(tag) != null

        val newFragment = fragmentByItemId(itemId)
        val transaction = childFragmentManager.beginTransaction()
        currentFragment?.let { transaction.hide(it) }
        if (isAlreadyFragment) {
            transaction.show(newFragment)
        } else {
            transaction.add(R.id.child_fragment, newFragment, tag)
        }
        transaction.commitNow()

        currentFragment = newFragment
    }

    override fun onResume() {
        super.onResume()
        window?.setBackgroundDrawableResource(uikit.R.color.constantBlack)

        Widget.updateAll()
    }
}