package com.tonapps.tonkeeper.fragment.main

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.core.currency.CurrencyUpdateWorker
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.widget.Widget
import com.tonapps.tonkeeper.event.RequestActionEvent
import com.tonapps.tonkeeper.event.WalletSettingsEvent
import com.tonapps.tonkeeper.extensions.isRecoveryPhraseBackup
import com.tonapps.tonkeeper.fragment.root.RootViewModel
import com.tonapps.tonkeeper.fragment.tonconnect.action.ConfirmActionFragment
import com.tonapps.tonkeeper.fragment.wallet.collectibles.CollectiblesScreen
import com.tonapps.tonkeeper.fragment.wallet.history.HistoryScreen
import com.tonapps.tonkeeper.ui.screen.picker.PickerScreen
import com.tonapps.tonkeeper.ui.screen.wallet.WalletScreen
import com.tonapps.uikit.color.constantBlackColor
import com.tonapps.uikit.color.drawable
import core.EventBus
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import com.tonapps.wallet.data.account.legacy.WalletLegacy
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.BottomTabsView

class MainFragment: BaseFragment(R.layout.fragment_main) {

    companion object {
        fun newInstance() = MainFragment()
    }

    private val mainViewModel: MainViewModel by viewModel()
    private val rootViewModel: RootViewModel by activityViewModel()

    private val fragmentMap by lazy {
        mapOf(
            R.id.wallet to WalletScreen.newInstance(),
            R.id.activity to HistoryScreen.newInstance(),
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

    private lateinit var fragmentContainer: FragmentContainerView
    private lateinit var bottomTabsView: BottomTabsView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CurrencyUpdateWorker.enable()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentContainer = view.findViewById(R.id.child_fragment)

        bottomTabsView = view.findViewById(R.id.bottom_tabs)
        bottomTabsView.doOnClick = { itemId ->
            setFragment(itemId, false)
        }
        bottomTabsView.doOnLongClick = { itemId ->
            if (itemId == R.id.wallet) {
                navigation?.add(PickerScreen.newInstance())
            }
        }
        collectFlow(mainViewModel.childBottomScrolled, bottomTabsView::setDivider)
        collectFlow(rootViewModel.openTabAction, this::forceSelectTab)

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

    private fun updateWalletState(wallet: WalletLegacy) {
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
        if (newFragment == currentFragment) {
            return
        }

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
        transaction.runOnCommit {
            bottomTabsView.setDivider(false)
        }
        transaction.commitNow()

        currentFragment = newFragment
    }

    override fun onResume() {
        super.onResume()
        window?.setBackgroundDrawable(requireContext().constantBlackColor.drawable)

        Widget.updateAll()
    }
}