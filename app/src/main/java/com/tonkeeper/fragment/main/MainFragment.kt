package com.tonkeeper.fragment.main

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeperx.R
import com.tonkeeper.App
import com.tonkeeper.core.currency.CurrencyUpdateWorker
import com.tonkeeper.core.history.HistoryHelper
import com.tonkeeper.core.widget.Widget
import com.tonkeeper.event.RequestActionEvent
import com.tonkeeper.event.WalletSettingsEvent
import com.tonkeeper.extensions.isRecoveryPhraseBackup
import com.tonkeeper.fragment.action.ConfirmActionFragment
import com.tonkeeper.fragment.wallet.collectibles.CollectiblesScreen
import com.tonkeeper.fragment.wallet.history.HistoryScreen
import com.tonkeeper.fragment.settings.main.SettingsScreen
import com.tonkeeper.fragment.wallet.main.WalletScreen
import core.EventBus
import kotlinx.coroutines.launch
import ton.wallet.Wallet
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation
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

    private val requestActionEvent = fun (event: RequestActionEvent) {
        post {
            val fragment = ConfirmActionFragment.newInstance(event.transaction)
            navigation?.add(fragment)
        }
    }

    private val walletSettingsUpdate = fun(_: WalletSettingsEvent) {
        requestWalletState()
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
            setFragment(itemId, false)
        }
        setFragment(R.id.wallet, false)

        lifecycleScope.launch {
            val wallet = App.walletManager.getWalletInfo() ?: return@launch
            HistoryHelper.subscribe(lifecycleScope, wallet.accountId)
            updateWalletState(wallet)
        }

        EventBus.subscribe(RequestActionEvent::class.java, requestActionEvent)
        EventBus.subscribe(WalletSettingsEvent::class.java, walletSettingsUpdate)
    }

    private fun requestWalletState() {
        lifecycleScope.launch {
            val wallet = App.walletManager.getWalletInfo() ?: return@launch
            updateWalletState(wallet)
        }
    }

    private fun updateWalletState(wallet: Wallet) {
        bottomTabsView.enableDot(R.id.settings, !wallet.isRecoveryPhraseBackup())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.unsubscribe(RequestActionEvent::class.java, requestActionEvent)
        EventBus.unsubscribe(WalletSettingsEvent::class.java, walletSettingsUpdate)
    }

    private fun fragmentByItemId(itemId: Int): BaseFragment {
        return fragmentMap[itemId] ?: throw IllegalArgumentException("Unknown itemId: $itemId")
    }

    fun forceSelectTab(itemId: Int) {
        bottomTabsView.setItemChecked(itemId)
        setFragment(itemId, true)
    }

    private fun setFragment(itemId: Int, scrollUp: Boolean) {
        val tag = itemId.toString()
        val isAlreadyFragment = childFragmentManager.findFragmentByTag(tag) != null

        val newFragment = fragmentByItemId(itemId)
        val transaction = childFragmentManager.beginTransaction()
        currentFragment?.let { transaction.hide(it) }
        if (isAlreadyFragment) {
            if (scrollUp) {
                (newFragment as? MainTabScreen<*, *, *>)?.upScroll()
            }
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