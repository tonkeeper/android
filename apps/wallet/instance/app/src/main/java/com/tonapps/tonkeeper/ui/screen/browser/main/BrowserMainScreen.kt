package com.tonapps.tonkeeper.ui.screen.browser.main

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.generated.Events.DappBrowser.DappBrowserType
import com.tonapps.dapp.screens.sessions.DisconnectDappFragment
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.browser.base.BrowserBaseScreen
import com.tonapps.tonkeeper.ui.screen.browser.base.BrowserBaseViewModel
import com.tonapps.tonkeeper.ui.screen.browser.main.list.connected.ConnectedAdapter
import com.tonapps.tonkeeper.ui.screen.browser.main.list.connected.ConnectedItem
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.ExploreAdapter
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.ExploreItem
import com.tonapps.tonkeeper.ui.screen.browser.more.BrowserMoreScreen
import com.tonapps.tonkeeperx.R
import kotlinx.coroutines.flow.filterNotNull
import org.koin.android.ext.android.inject
import uikit.drawable.HeaderDrawable
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.isMaxScrollReached
import uikit.navigation.Navigation.Companion.navigation
import uikit.utils.RecyclerVerticalScrollListener
import uikit.widget.SlideBetweenView

class BrowserMainScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_browser_main, wallet) {

    override val fragmentName: String = "BrowserMainScreen"

    private val baseFragment: BrowserBaseScreen? by lazy {
        BrowserBaseScreen.from(this)
    }

    private val baseViewModel: BrowserBaseViewModel? by lazy {
        baseFragment?.viewModel
    }

    override val viewModel: BrowserMainViewModel by walletViewModel()

    private val environment: Environment by inject()

    private val connectedAdapter = ConnectedAdapter { item ->
        navigation?.addForResult(
            DisconnectDappFragment.newInstance(
                name = item.name,
                iconUrl = item.app.iconUrl,
            )
        ) { bundle ->
            if (bundle.getBoolean(DisconnectDappFragment.RESULT_CONFIRMED)) {
                viewModel.disconnect(item)
            }
        }
    }

    private val exploreAdapter by lazy {
        ExploreAdapter(
            onMoreClick = { id ->
                baseFragment?.addFragment(
                    BrowserMoreScreen.newInstance(
                        screenContext.wallet,
                        id,
                        viewModel.selectedChain.value?.id,
                    )
                )
            },
            selectedChain = viewModel.selectedChain,
            onChainSelected = viewModel::onChainSelected,
            environment = environment,
        )
    }

    private val scrollListener = object : RecyclerVerticalScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, verticalScrollOffset: Int) {
            baseViewModel?.apply {
                headerDrawable.setDivider(verticalScrollOffset > 0)
                setBottomScrolled(!recyclerView.isMaxScrollReached)
            }
        }
    }

    private lateinit var headerDrawable: HeaderDrawable
    private lateinit var headerView: View
    private lateinit var slideView: SlideBetweenView
    private lateinit var exploreTabView: AppCompatTextView
    private lateinit var connectedTabView: AppCompatTextView
    private lateinit var connectedPlaceholder: View
    private lateinit var connectedPlaceholderLottie: LottieAnimationView
    private lateinit var connectedListView: RecyclerView
    private lateinit var exploreListView: RecyclerView

    private val activeListView: RecyclerView
        get() = if (exploreTabView.background != null) exploreListView else connectedListView

    private val isConnectedTabActive: Boolean
        get() = connectedTabView.background != null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerDrawable = HeaderDrawable(requireContext())
        headerView = view.findViewById(R.id.header)
        headerView.background = headerDrawable

        exploreTabView = view.findViewById(R.id.explore_tab)
        exploreTabView.setOnClickListener { clickTab(it as AppCompatTextView, userInitiated = true) }

        connectedTabView = view.findViewById(R.id.connected_tab)
        connectedTabView.setOnClickListener { clickTab(it as AppCompatTextView, userInitiated = true) }

        slideView = view.findViewById(R.id.slide)

        connectedPlaceholder = view.findViewById(R.id.connected_placeholder)
        connectedPlaceholderLottie = view.findViewById(R.id.connected_placeholder_lottie)
        connectedPlaceholderLottie.setAnimation(CONNECTED_APPS_LOTTIE)
        connectedPlaceholderLottie.repeatCount = 0

        connectedListView = view.findViewById(R.id.connected_list)
        connectedListView.adapter = connectedAdapter
        connectedListView.layoutManager = object : GridLayoutManager(context, 4) {
            override fun supportsPredictiveItemAnimations(): Boolean = false
        }

        exploreListView = view.findViewById(R.id.explore_list)
        exploreListView.adapter = exploreAdapter
        exploreListView.layoutManager = object : GridLayoutManager(context, 4) {

            init {
                spanSizeLookup = object : SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return when (exploreAdapter.getItemViewType(position)) {
                            ExploreItem.TYPE_TITLE,
                            ExploreItem.TYPE_BANNERS,
                            ExploreItem.TYPE_ADS,
                            ExploreItem.TYPE_CHAIN_FILTER,
                            ExploreItem.TYPE_SPACE,
                            ExploreItem.TYPE_CHAIN_EMPTY,
                            -> 4
                            else -> 1
                        }
                    }
                }
            }

            override fun supportsPredictiveItemAnimations(): Boolean = false
        }
        exploreListView.addItemDecoration(object : RecyclerView.ItemDecoration() {

            private val offsetHorizontal = requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium)

            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                super.getItemOffsets(outRect, view, parent, state)
                val position = parent.getChildAdapterPosition(view)
                if (position == 0) {
                    return
                }
                val item = exploreAdapter.currentList[position] ?: return
                if (item is ExploreItem.Title) {
                    outRect.left = offsetHorizontal
                    outRect.right = offsetHorizontal
                } else if (item is ExploreItem.App) {
                    val spanIndex = (view.layoutParams as GridLayoutManager.LayoutParams).spanIndex
                    if (spanIndex == 0) {
                        outRect.left = offsetHorizontal
                        outRect.right = offsetHorizontal / 2
                    } else if (spanIndex == 4 - 1) {
                        outRect.left = offsetHorizontal / 2
                        outRect.right = offsetHorizontal
                    } else {
                        outRect.left = offsetHorizontal / 2
                        outRect.right = offsetHorizontal / 2
                    }
                }
            }
        })
        collectFlow(viewModel.uiConnectedItemsFlow, ::setConnectedList)
        collectFlow(viewModel.uiExploreItemsFlow, exploreAdapter::submitList)

        baseViewModel?.insetsRootFlow?.let { insets ->
            collectFlow(insets, ::onApplyWindowInsets)
        }

        baseViewModel?.let { baseVM ->
            collectFlow(baseVM.pendingChainFlow.filterNotNull()) { network ->
                viewModel.applyChainFromDeepLink(network) { applied ->
                    baseVM.consumePendingChain()
                    if (applied && getView() != null) {
                        clickTab(exploreTabView)
                    }
                }
            }
        }

        exploreTabView.isVisible = !viewModel.isDappsDisabled

        clickTab(if (viewModel.isDappsDisabled) connectedTabView else exploreTabView, animated = false)
    }

    private fun onApplyWindowInsets(insets: WindowInsetsCompat) {
        val statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        headerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusInsets.top
        }
    }

    private fun updateScrollHandler() {
        detachScrollHandler()
        attachScrollHandler()
    }

    private fun attachScrollHandler() {
        scrollListener.attach(activeListView)
    }

    private fun detachScrollHandler() {
        scrollListener.detach()
    }

    override fun onResume() {
        super.onResume()
        attachScrollHandler()
        playAppsLottieIfNeeded()
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

    private fun setConnectedList(items: List<ConnectedItem>) {
        if (items.isEmpty()) {
            connectedListView.visibility = View.GONE
            connectedPlaceholder.visibility = View.VISIBLE
            playAppsLottieIfNeeded()
        } else {
            connectedListView.visibility = View.VISIBLE
            connectedPlaceholder.visibility = View.GONE
            connectedAdapter.submitList(items)
        }
    }

    private fun playAppsLottieIfNeeded() {
        if (!connectedPlaceholder.isVisible || !isConnectedTabActive) {
            return
        }
        connectedPlaceholderLottie.cancelAnimation()
        connectedPlaceholderLottie.progress = 0f
        connectedPlaceholderLottie.playAnimation()
    }

    private fun clickTab(
        view: AppCompatTextView,
        animated: Boolean = true,
        userInitiated: Boolean = false
    ) {
        val isActive = view.background != null
        if (isActive) {
            return
        }

        val type: DappBrowserType
        if (view.id == R.id.connected_tab) {
            slideView.next(animated)
            connectedTabView.setBackgroundResource(uikit.R.drawable.bg_button_secondary)
            exploreTabView.background = null
            playAppsLottieIfNeeded()
            type = DappBrowserType.Connected
        } else if (view.id == R.id.explore_tab) {
            slideView.prev(animated)
            exploreTabView.setBackgroundResource(uikit.R.drawable.bg_button_secondary)
            connectedTabView.background = null
            type = DappBrowserType.Explore
        } else {
            type = DappBrowserType.Explore
        }

        if (userInitiated) {
            baseViewModel?.trackTabClick(type)
        } else {
            baseViewModel?.setActiveTab(type)
        }

        updateScrollHandler()
    }

    companion object {
        private const val CONNECTED_APPS_LOTTIE = "ic_apps_28_opt.json"

        fun newInstance(wallet: WalletEntity) = BrowserMainScreen(wallet)
    }

}