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
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.extensions.query
import com.tonapps.log.L
import com.tonapps.tonkeeper.extensions.removeAllFragments
import com.tonapps.tonkeeper.koin.serverFlags
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.browser.base.BrowserBaseScreen
import com.tonapps.portfolio.PortfolioFragment
import com.tonapps.portfolio.analytics.PortfolioAnalytics
import com.tonapps.tonkeeper.ui.screen.wallet.main.WalletScreen
import com.tonapps.trading.screens.shelves.ShelvesFragment
import com.tonapps.tonkeeper.ui.screen.root.RootEvent
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeper.ui.screen.swap.SwapScreen
import com.tonapps.tonkeeper.ui.screen.wallet.picker.PickerScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.backgroundPageColor
import com.tonapps.uikit.color.constantBlackColor
import com.tonapps.uikit.color.drawable
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.getViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.drawable.BarDrawable
import uikit.extensions.activity
import uikit.extensions.collectFlow
import uikit.extensions.isMaxScrollReached
import uikit.extensions.roundTop
import uikit.extensions.scale
import uikit.utils.RecyclerVerticalScrollListener
import uikit.widget.BottomTabsView

class MainScreen: BaseWalletScreen<ScreenContext.None>(R.layout.fragment_main, ScreenContext.None) {

    override val fragmentName: String = "MainScreen"

    abstract class MainTabChildFragment<C : ScreenContext>(
        @LayoutRes layoutId: Int,
        screenContext: C,
    ) : BaseWalletScreen<C>(layoutId, screenContext) {

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

    abstract class Child(
        @LayoutRes layoutId: Int,
        wallet: WalletEntity,
    ) : MainTabChildFragment<ScreenContext.Wallet>(layoutId, ScreenContext.Wallet(wallet)) {

        val wallet: WalletEntity
            get() = screenContext.wallet
    }

    abstract class ChildScreen(
        @LayoutRes layoutId: Int,
    ) : MainTabChildFragment<ScreenContext.None>(layoutId, ScreenContext.None)

    override val viewModel: MainViewModel by viewModel()
    private val rootViewModel: RootViewModel by activityViewModel()

    private val fragments: MutableMap<Int, Fragment> = mutableMapOf()

    private var currentWalletId: String? = null

    private lateinit var bottomTabsView: BottomTabsView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childFragmentManager.removeAllFragments()

        bottomTabsView = view.findViewById(R.id.bottom_tabs)
        bottomTabsView.setBgColor(requireContext().backgroundPageColor)
        bottomTabsView.doOnLongClick = { itemId ->
            if (itemId == R.id.wallet) {
                navigation?.add(PickerScreen.newInstance())
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
            val network = if (itemId == R.id.browser) it.link.query("network") else null
            setFragment(itemId, it.wallet, it.from, extra, true, network)
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
        collectFlow(viewModel.selectedWalletFlow.filterNotNull()) { wallet ->
            applyWallet(wallet)
            setFragment(bottomTabsView.selectedItemId, wallet, "wallet", null, false)
        }

        bottomTabsView.toggleItem(R.id.trading, true)
        bottomTabsView.setLottieTabIcons(
            mapOf(
                R.id.wallet to uikit.R.raw.lottie_wallet,
                R.id.trading to uikit.R.raw.lottie_trade,
                R.id.browser to uikit.R.raw.lottie_browser,
            ),
        )
    }

    override fun onBackPressed(): Boolean {
        val visibleFragment = childFragmentManager.fragments.find {
            !it.isHidden && !it.isDetached
        }
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
            setFragment(itemId, wallet, "wallet", null, false)
            if (itemId == R.id.browser) {
                analytics?.simpleTrackEvent("browser_click")
            }
        }
    }

    private fun getFragment(itemId: Int, wallet: WalletEntity): Fragment {
        return fragments[itemId] ?: createFragment(itemId, wallet).also {
            fragments[itemId] = it
        }
    }

    private fun createFragment(itemId: Int, wallet: WalletEntity): Fragment {
        val fragment = when (itemId) {
            R.id.wallet -> if (wallet.type == WalletType.Multichain) {
                PortfolioFragment.newInstance()
            } else {
                WalletScreen.newInstance(wallet)
            }
            R.id.trading -> ShelvesFragment()
            R.id.browser -> BrowserBaseScreen.newInstance(wallet)
            else -> throw IllegalArgumentException("Unknown itemId: $itemId")
        }
        return fragment
    }

    private fun setFragment(itemId: Int, wallet: WalletEntity, from: String, extra: String?, forceScrollUp: Boolean, network: String? = null) {
        viewModel.setData(wallet, itemId)
        setFragment(getFragment(itemId, wallet), forceScrollUp, from, extra, 0, wallet, network)
    }

    private fun setFragment(fragment: Fragment, forceScrollUp: Boolean, from: String, extra: String?, attempt: Int, wallet: WalletEntity, network: String? = null) {
        if (attempt > 3) {
            throw IllegalStateException("Failed to set main fragment")
        }

        if (childFragmentManager.isStateSaved) {
            return
        }

        if (fragment.isAdded && !fragment.isHidden) {
            (fragment as? MainTabChildFragment<*>)?.scrollUp()
            applyBrowserExtras(fragment, extra, network)
            return
        }
        val transaction = childFragmentManager.beginTransaction()
        childFragmentManager.fragments.filter {
            it != fragment && !it.isHidden
        }.forEach { transaction.hide(it) }

        if (fragment.isAdded) {
            transaction.show(fragment)
            if (forceScrollUp) {
                (fragment as? MainTabChildFragment<*>)?.scrollUp()
            }
        } else {
            transaction.add(R.id.child_fragment, fragment)
        }
        transaction.runOnCommit {
            checkBottomDivider(fragment)
            if (fragment is BrowserBaseScreen) {
                fragment.trackBrowserOpen(from)
                applyBrowserExtras(fragment, extra, network)
            } else if (fragment is ShelvesFragment) {
                if (!extra.isNullOrBlank()) {
                    fragment.scrollToShelf(extra)
                }
            } else if (fragment is PortfolioFragment || fragment is WalletScreen) {
                PortfolioAnalytics.open(wallet)
            }
        }
        try {
            transaction.commitNow()
            L.d("MainScreenLog", "Set fragment: $fragment")
        } catch (e: Throwable) {
            L.e("MainScreenLog", "Failed to set fragment", e)
            FirebaseCrashlytics.getInstance().recordException(e)
            postDelayed(1000) {
                setFragment(fragment, forceScrollUp, from, extra, attempt + 1, wallet, network)
            }
        }
    }

    private fun applyBrowserExtras(fragment: Fragment, category: String?, network: String?) {
        if (fragment !is BrowserBaseScreen) {
            return
        }
        if (!category.isNullOrBlank()) {
            fragment.openCategory(category)
        }
        if (!network.isNullOrBlank()) {
            fragment.openNetwork(network)
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
        if (deeplink.startsWith("tonkeeper://browser")) {
            return R.id.browser
        } else if (deeplink.startsWith("tonkeeper://trading")) {
            return R.id.trading
        }
        return R.id.wallet
    }

    companion object {

        fun newInstance() = MainScreen()
    }

}