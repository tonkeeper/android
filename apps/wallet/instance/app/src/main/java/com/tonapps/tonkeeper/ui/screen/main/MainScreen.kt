package com.tonapps.tonkeeper.ui.screen.main

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.extensions.removeAllFragments
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.ui.screen.browser.main.BrowserMainScreen
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeper.ui.screen.collectibles.CollectiblesScreen
import com.tonapps.tonkeeper.ui.screen.events.EventsScreen
import com.tonapps.tonkeeper.ui.screen.wallet.picker.PickerScreen
import com.tonapps.tonkeeper.ui.screen.root.RootEvent
import com.tonapps.tonkeeper.ui.screen.swap.SwapScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.WalletScreen
import com.tonapps.uikit.color.constantBlackColor
import com.tonapps.uikit.color.drawable
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import org.koin.androidx.viewmodel.ext.android.getViewModel
import uikit.base.BaseFragment
import uikit.drawable.BarDrawable
import uikit.extensions.collectFlow
import uikit.extensions.isMaxScrollReached
import uikit.navigation.Navigation.Companion.navigation
import uikit.utils.RecyclerVerticalScrollListener
import uikit.widget.BottomTabsView

class MainScreen: BaseWalletScreen<ScreenContext.None>(R.layout.fragment_main, ScreenContext.None) {

    abstract class Child(
        @LayoutRes layoutId: Int,
        wallet: WalletEntity,
    ): BaseWalletScreen<ScreenContext.Wallet>(layoutId, ScreenContext.Wallet(wallet)) {

        val mainViewModel: MainViewModel by lazy {
            requireParentFragment().getViewModel()
        }

        private val scrollListener = object : RecyclerVerticalScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, verticalScrollOffset: Int) {
                getHeaderDividerOwner()?.setDivider(verticalScrollOffset > 0)
                mainViewModel.setBottomScrolled(!recyclerView.isMaxScrollReached)
            }
        }

        abstract fun getRecyclerView(): RecyclerView?

        abstract fun getHeaderDividerOwner(): BarDrawable.BarDrawableOwner?

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

    private lateinit var bottomTabsView: BottomTabsView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childFragmentManager.removeAllFragments()

        bottomTabsView = view.findViewById(R.id.bottom_tabs)
        bottomTabsView.doOnLongClick = { itemId ->
            if (itemId == R.id.wallet) {
                navigation?.add(PickerScreen.newInstance())
            }
        }
        collectFlow(viewModel.childBottomScrolled, bottomTabsView::setDivider)

        /*collectFlow(rootViewModel.eventFlow.filterIsInstance<RootEvent.OpenTab>().map {
            mainDeepLinks[it.link]
        }.filterNotNull(), this::forceSelectTab)*/

        collectFlow(rootViewModel.eventFlow.filterIsInstance<RootEvent.Swap>()) {
            navigation?.add(SwapScreen.newInstance(it.uri, it.address, it.from, it.to))
        }
        collectFlow(viewModel.selectedWalletFlow) { wallet ->
            val browserTabEnabled = (wallet.type == Wallet.Type.Default || wallet.isExternal)
            bottomTabsView.toggleItem(R.id.browser, browserTabEnabled)
            val itemId = if (childFragmentManager.fragments.isEmpty() || (!browserTabEnabled && bottomTabsView.selectedItemId == R.id.browser)) {
                R.id.wallet
            } else {
                bottomTabsView.selectedItemId
            }
            applyWallet(wallet)
            setFragment(itemId, wallet)
        }
    }

    private fun applyWallet(wallet: WalletEntity) {
        if (fragments.isNotEmpty()) {
            childFragmentManager.removeAllFragments()
            fragments.clear()
        }

        bottomTabsView.doOnClick = { itemId ->
            setFragment(itemId, wallet)
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
            R.id.activity -> EventsScreen.newInstance(wallet)
            R.id.collectibles -> CollectiblesScreen.newInstance(wallet)
            R.id.browser -> BrowserMainScreen.newInstance(wallet)
            else -> throw IllegalArgumentException("Unknown itemId: $itemId")
        }
        return fragment
    }

    private fun setFragment(itemId: Int, wallet: WalletEntity) {
        setFragment(getFragment(itemId, wallet))
    }

    private fun setFragment(activeFragment: Fragment) {
        val transaction = childFragmentManager.beginTransaction()
        for (fragment in childFragmentManager.fragments) {
            if (fragment != activeFragment) {
                transaction.hide(fragment)
            }
        }
        if (activeFragment.isAdded) {
            transaction.show(activeFragment)
        } else {
            transaction.add(R.id.child_fragment, activeFragment)
        }
        transaction.commitNow()
    }

    override fun onResume() {
        super.onResume()
        window?.setBackgroundDrawable(requireContext().constantBlackColor.drawable)
    }

    companion object {

        private val mainDeepLinks = mapOf(
            "tonkeeper://wallet" to R.id.wallet,
            "tonkeeper://activity" to R.id.activity,
            "tonkeeper://browser" to R.id.browser,
            "tonkeeper://collectibles" to R.id.collectibles
        )

        fun isSupportedDeepLink(uri: String): Boolean {
            return mainDeepLinks.containsKey(uri)
        }

        fun newInstance() = MainScreen()
    }

}