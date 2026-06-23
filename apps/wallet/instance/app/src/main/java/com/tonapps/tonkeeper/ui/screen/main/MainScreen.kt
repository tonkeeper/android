package com.tonapps.tonkeeper.ui.screen.main

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.extensions.query
import com.tonapps.log.L
import com.tonapps.tonkeeper.extensions.isLightTheme
import com.tonapps.tonkeeper.extensions.removeAllFragments
import com.tonapps.tonkeeper.koin.serverFlags
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.browser.base.BrowserBaseScreen
import com.tonapps.tonkeeper.ui.screen.collectibles.main.CollectiblesScreen
import com.tonapps.core.flags.TooltipManager
import com.tonapps.core.flags.TooltipState
import com.tonapps.core.flags.WalletFeature
import com.tonapps.core.flags.WalletTooltip
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxEventsScreen
import com.tonapps.tonkeeper.ui.screen.root.RootEvent
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeper.ui.screen.swap.SwapScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.WalletScreen
import com.tonapps.tonkeeper.ui.screen.wallet.picker.PickerScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.trading.screens.shelves.ShelvesFragment
import com.tonapps.wallet.localization.Localization
import com.tonapps.uikit.color.backgroundPageColor
import com.tonapps.uikit.color.backgroundTransparentColor
import com.tonapps.uikit.color.constantBlackColor
import com.tonapps.uikit.color.drawable
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.drawable.BarDrawable
import uikit.extensions.activity
import uikit.extensions.collectFlow
import uikit.extensions.isMaxScrollReached
import uikit.extensions.roundTop
import uikit.extensions.scale
import uikit.utils.RecyclerVerticalScrollListener
import uikit.extensions.dp
import uikit.widget.BalloonTooltip
import uikit.widget.BottomTabsView

class MainScreen: BaseWalletScreen<ScreenContext.None>(R.layout.fragment_main, ScreenContext.None) {

    override val fragmentName: String = "MainScreen"

    abstract class Child(
        @LayoutRes layoutId: Int,
        wallet: WalletEntity,
    ): WalletContextScreen(layoutId, wallet) {

        val mainViewModel: MainViewModel by lazy {
            requireParentFragment().getViewModel()
        }

        private val scrollListener = object : RecyclerVerticalScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, verticalScrollOffset: Int) {
                recyclerView.postOnAnimation {
                    if (recyclerView.isAttachedToWindow) {
                        getTopBarDrawable()?.setDivider(verticalScrollOffset > 0)
                        mainViewModel.setBottomScrolled(!recyclerView.isMaxScrollReached)
                    }
                }
            }
        }

        abstract fun getRecyclerView(): RecyclerView?

        abstract fun getTopBarDrawable(): BarDrawable?

        open fun scrollUp() {
            getRecyclerView()?.scrollToPosition(0)
        }

        override fun onResume() {
            super.onResume()
            attachScrollHandler()
        }

        override fun onPause() {
            super.onPause()
            detachScrollHandler()
        }

        override fun onHiddenChanged(hidden: Boolean) {
            super.onHiddenChanged(hidden)
            if (hidden) {
                detachScrollHandler()
            } else {
                attachScrollHandler()
            }
        }

        private fun attachScrollHandler() {
            getRecyclerView()?.let {
                scrollListener.attach(it)
            }
        }

        private fun detachScrollHandler() {
            scrollListener.detach()
        }
    }

    override val viewModel: MainViewModel by viewModel()
    private val rootViewModel: RootViewModel by activityViewModel()

    private val fragments: MutableMap<Int, Fragment> = mutableMapOf()

    private var currentWalletId: String? = null

    private lateinit var bottomTabsView: BottomTabsView

    private var tradingTabTooltip: BalloonTooltip? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childFragmentManager.removeAllFragments()

        bottomTabsView = view.findViewById(R.id.bottom_tabs)
        if (requireContext().isLightTheme) {
            bottomTabsView.setBgColor(requireContext().backgroundPageColor)
        } else {
            bottomTabsView.setBgColor(requireContext().backgroundTransparentColor)
        }
        bottomTabsView.doOnLongClick = { itemId ->
            if (itemId == R.id.wallet) {
                navigation?.add(PickerScreen.newInstance(from = getCurrentFrom()))
            }
        }
        collectFlow(viewModel.childBottomScrolled) {
            if (bottomTabsView.selectedItemId == R.id.browser) {
                bottomTabsView.setDivider(false)
            } else {
                bottomTabsView.setDivider(it)
            }
        }
        rootViewModel.eventFlow.filterIsInstance<RootEvent.OpenTab>().onEach {
            val itemId = resolveId(it.link.toString())
            bottomTabsView.selectedItemId = itemId
            val extra = when (itemId) {
                R.id.browser -> it.link.query("category")
                R.id.trading -> it.link.query("shelf")
                else -> null
            }
            setFragment(itemId, it.wallet, it.from, extra, true)
            parentClearState()
        }.launchIn(lifecycleScope)

        collectFlow(rootViewModel.eventFlow.filterIsInstance<RootEvent.Swap>()) {
            val fromCurrency = WalletCurrency.of(it.from) ?: WalletCurrency.TON
            val toCurrency = it.to?.let { to -> WalletCurrency.of(to) }
            navigation?.add(SwapScreen.newInstance(
                wallet = it.wallet,
                fromToken = fromCurrency,
                toToken = toCurrency,
                nativeSwap = context?.serverFlags?.disableNativeSwap != true,
                uri = it.uri,
                fromTokenRaw = it.from,
                toTokenRaw = it.to
            ))
        }
        collectFlow(viewModel.selectedWalletFlow) { wallet ->
            applyWallet(wallet)
            setFragment(bottomTabsView.selectedItemId, wallet, "wallet",null, false)
        }

        collectFlow(viewModel.disbleNftsFlow) {
            bottomTabsView.toggleItem(R.id.collectibles, !it)
        }

        val isTradingEnabled = WalletFeature.TradingTab.isEnabled
        bottomTabsView.toggleItem(R.id.activity, !isTradingEnabled)
        bottomTabsView.toggleItem(R.id.trading, isTradingEnabled)
        if (isTradingEnabled) {
            bottomTabsView.post { tryShowTradingTabTooltip() }
        }
    }

    override fun onDestroyView() {
        dismissTradingTabTooltip()
        super.onDestroyView()
    }

    private fun tryShowTradingTabTooltip() {
        if (tradingTabTooltip != null) {
            return
        }
        if (!WalletFeature.TradingTab.isEnabled) {
            return
        }
        if (!WalletTooltip.TradingTab.shouldShow) {
            return
        }
        val tab = bottomTabsView.findTabView(R.id.trading)
        if (tab == null) {
            return
        }
        tab.post {
            if (tradingTabTooltip != null) {
                return@post
            }
            if (!tab.isAttachedToWindow || tab.visibility != View.VISIBLE) {
                return@post
            }
            tradingTabTooltip = BalloonTooltip.show(
                anchorView = tab,
                badgeText = getString(Localization.tooltip_new),
                messageText = getString(Localization.tooltip_trade_us_stocks_etfs),
                placement = BalloonTooltip.Placement.TOP,
                offset = 12.dp,
                autoDismissMs = BalloonTooltip.NO_AUTO_DISMISS,
                onShown = {
                    TooltipManager.markShownInSession(WalletTooltip.TradingTab.key)
                    TooltipManager.incrementShowCount(WalletTooltip.TradingTab.key)
                    markTradingTabTooltipShown()
                },
                onClickListener = {
                    markTradingTabTooltipShown()
                    dismissTradingTabTooltip()
                },
            )
        }
    }

    private fun markTradingTabTooltipShown() {
        if (WalletTooltip.TradingTab.state != TooltipState.ALWAYS) {
            TooltipManager.setState(WalletTooltip.TradingTab.key, TooltipState.SHOWN)
        }
    }

    private fun dismissTradingTabTooltip() {
        tradingTabTooltip?.dismiss()
        tradingTabTooltip = null
    }

    override fun onBackPressed(): Boolean {
        val visibleFragment = childFragmentManager.fragments.find {
            !it.isHidden && !it.isDetached
        } as BaseFragment
        return if (visibleFragment is BrowserBaseScreen) {
            visibleFragment.onBackPressed()
        } else {
            super.onBackPressed()
        }
    }

    private fun parentClearState() {
        val activity = context?.activity ?: return
        val view = activity.findViewById<View>(uikit.R.id.root_container)
        view.roundTop(0)
        view.scale = 1f
        view.alpha = 1f
    }

    private fun applyWallet(wallet: WalletEntity) {
        val walletChanged = currentWalletId != null && currentWalletId != wallet.id
        if (walletChanged && fragments.isNotEmpty()) {
            childFragmentManager.removeAllFragments()
            fragments.clear()
        }
        currentWalletId = wallet.id

        bottomTabsView.doOnClick = { itemId ->
            if (itemId == R.id.trading) {
                dismissTradingTabTooltip()
            }
            setFragment(itemId, wallet, "wallet",null, false)
            if (itemId == R.id.browser) {
                analytics?.simpleTrackEvent("browser_click")
            }
        }
    }

    private fun getCurrentFrom(): String {
        return when(bottomTabsView.selectedItemId) {
            R.id.wallet -> "wallet"
            R.id.activity -> "activity"
            R.id.trading -> "trading"
            R.id.collectibles -> "collectibles"
            R.id.browser -> "browser"
            else -> "unknown"
        }
    }

    private fun getFragment(itemId: Int, wallet: WalletEntity): Fragment {
        return fragments[itemId] ?: createFragment(itemId, wallet).also {
            fragments[itemId] = it
        }
    }

    private fun createFragment(itemId: Int, wallet: WalletEntity): Fragment {
        val fragment = when(itemId) {
            R.id.wallet -> WalletScreen.newInstance(wallet)
            R.id.activity -> TxEventsScreen.newInstance(wallet, canGoBack = false)
            R.id.trading -> ShelvesFragment()
            R.id.collectibles -> CollectiblesScreen.newInstance(wallet)
            R.id.browser -> BrowserBaseScreen.newInstance(wallet)
            else -> throw IllegalArgumentException("Unknown itemId: $itemId")
        }
        return fragment
    }

    private fun setFragment(itemId: Int, wallet: WalletEntity, from: String, extra: String?, forceScrollUp: Boolean) {
        viewModel.setData(wallet, itemId)
        setFragment(getFragment(itemId, wallet), forceScrollUp, from, extra, 0)
    }

    private fun setFragment(fragment: Fragment, forceScrollUp: Boolean, from: String, extra: String?, attempt: Int) {
        if (attempt > 3) {
            throw IllegalStateException("Failed to set main fragment")
        }

        if (childFragmentManager.isStateSaved) {
            return
        }

        if (fragment.isAdded && !fragment.isHidden) {
            (fragment as? Child)?.scrollUp()
            return
        }
        val transaction = childFragmentManager.beginTransaction()
        childFragmentManager.fragments.filter {
            it != fragment && !it.isHidden
        }.forEach { transaction.hide(it) }

        if (fragment.isAdded) {
            transaction.show(fragment)
            if (forceScrollUp) {
                (fragment as? Child)?.scrollUp()
            }
        } else {
            transaction.add(R.id.child_fragment, fragment)
        }
        transaction.runOnCommit {
            checkBottomDivider(fragment)
            if (fragment is BrowserBaseScreen) {
                analytics?.simpleTrackScreenEvent("browser_open", from)
                if (!extra.isNullOrBlank()) {
                    fragment.openCategory(extra)
                }
            } else if (fragment is ShelvesFragment) {
                if (!extra.isNullOrBlank()) {
                    fragment.scrollToShelf(extra)
                }
            } else if (fragment is TxEventsScreen) {
                analytics?.simpleTrackScreenEvent("history_open", from)
            } else if (fragment is CollectiblesScreen) {
                analytics?.simpleTrackScreenEvent("collectibles_open", from)
            } else if (fragment is WalletScreen) {
                analytics?.simpleTrackEvent("wallet_open", hashMapOf(
                    "from" to from,
                    "wallet_type" to fragment.wallet.version.title
                ))
            }
        }
        try {
            transaction.commitNow()
            L.d("MainScreenLog", "Set fragment: $fragment")
        } catch (e: Throwable) {
            L.e("MainScreenLog", "Failed to set fragment", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            postDelayed(1000) {
                setFragment(fragment, forceScrollUp, from,extra, attempt + 1)
            }
        }
    }

    private fun checkBottomDivider(fragment: Fragment) {
        if (fragment is BrowserBaseScreen) {
            bottomTabsView.setDivider(false)
        }
    }

    override fun onResume() {
        super.onResume()
        window?.setBackgroundDrawable(requireContext().constantBlackColor.drawable)
    }

    private fun resolveId(deeplink: String): Int {
        if (deeplink.startsWith("tonkeeper://activity")) {
            return R.id.activity
        } else if (deeplink.startsWith("tonkeeper://browser")) {
            return R.id.browser
        } else if (deeplink.startsWith("tonkeeper://collectibles")) {
            return R.id.collectibles
        } else if (deeplink.startsWith("tonkeeper://trading")) {
            return R.id.trading
        }
        return R.id.wallet

    }

    companion object {

        fun newInstance() = MainScreen()
    }

}