package com.tonkeeper.fragment.main

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.core.currency.CurrencyUpdateWorker
import com.tonkeeper.core.history.HistoryHelper
import com.tonkeeper.core.widget.Widget
import com.tonkeeper.fragment.wallet.collectibles.CollectiblesScreen
import com.tonkeeper.fragment.wallet.history.HistoryScreen
import com.tonkeeper.fragment.settings.main.SettingsScreen
import com.tonkeeper.fragment.wallet.main.WalletScreen
import kotlinx.coroutines.launch
import uikit.base.BaseFragment
import uikit.widget.BottomTabsView

class MainFragment: BaseFragment(R.layout.fragment_main) {

    companion object {
        fun newInstance() = MainFragment()
    }

    private val fragmentMap by lazy {
        mapOf(
            R.id.wallet to WalletScreen.newInstance(),
            R.id.activity to HistoryScreen.newInstance(),
            R.id.settings to SettingsScreen.newInstance(),
            R.id.collectibles to CollectiblesScreen.newInstance()
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
        bottomTabsView.doOnClick = { itemId ->
            setFragment(itemId)
        }
        setFragment(R.id.wallet)

        lifecycleScope.launch {
            val wallet = App.walletManager.getWalletInfo() ?: return@launch
            HistoryHelper.subscribe(lifecycleScope, wallet.accountId)
        }
    }

    private fun fragmentByItemId(itemId: Int): BaseFragment {
        return fragmentMap[itemId] ?: throw IllegalArgumentException("Unknown itemId: $itemId")
    }

    fun forceSelectTab(itemId: Int) {
        bottomTabsView.setItemChecked(itemId)
        setFragment(itemId)
    }

    private fun setFragment(itemId: Int) {
        val tag = itemId.toString()
        val isAlreadyFragment = childFragmentManager.findFragmentByTag(tag) != null

        val newFragment = fragmentByItemId(itemId)
        val transaction = childFragmentManager.beginTransaction()
        currentFragment?.let { transaction.hide(it) }
        if (isAlreadyFragment) {
            (newFragment as? MainTabScreen<*, *, *>)?.onUpScroll()
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