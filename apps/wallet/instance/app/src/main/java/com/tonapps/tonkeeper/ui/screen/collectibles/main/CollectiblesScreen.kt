package com.tonapps.tonkeeper.ui.screen.collectibles.main

import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tonapps.deposit.screens.qr.QrAssetFragment
import com.tonapps.tonkeeper.extensions.isLightTheme
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.base.UiListState
import com.tonapps.tonkeeper.ui.screen.collectibles.main.list.Adapter
import com.tonapps.tonkeeper.ui.screen.collectibles.manage.CollectiblesManageScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.backgroundPageColor
import com.tonapps.uikit.color.backgroundTransparentColor
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.clearDrawables
import uikit.extensions.collectFlow
import uikit.extensions.dp
import uikit.extensions.getDrawable
import uikit.extensions.setRightDrawable
import uikit.extensions.topScrolled
import uikit.widget.EmptyLayout
import uikit.widget.HeaderView

class CollectiblesScreen : BaseWalletScreen<ScreenContext.None>(
    R.layout.fragment_main_list,
    ScreenContext.None,
), BaseFragment.SwipeBack {

    override val fragmentName: String = "CollectiblesScreen"

    private val from: String by lazy { requireArguments().getString(ARG_FROM) ?: "unknown" }

    override val viewModel: CollectiblesViewModel by viewModel()

    private val adapter = Adapter()

    private lateinit var headerView: HeaderView
    private lateinit var refreshView: SwipeRefreshLayout
    private lateinit var listView: RecyclerView
    private lateinit var emptyView: EmptyLayout

    private val tonOnlyInfoDialog: CollectiblesTonOnlyDialog by lazy {
        CollectiblesTonOnlyDialog(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics?.simpleTrackScreenEvent("collectibles_open", from)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        setupHeaderLayout()

        refreshView = view.findViewById(R.id.refresh)
        refreshView.setOnRefreshListener { viewModel.refresh() }

        listView = view.findViewById(R.id.list)
        listView.updatePadding(top = 0)
        listView.layoutManager = object : GridLayoutManager(context, 3) {
            override fun supportsPredictiveItemAnimations(): Boolean = false
        }
        listView.adapter = adapter
        collectFlow(listView.topScrolled, headerView::setDivider)

        emptyView = view.findViewById(R.id.empty)
        emptyView.doOnButtonClick = { openQRCode() }

        setupTonOnlySubtitle()

        collectFlow(viewModel.uiListStateFlow) { state ->
            when (state) {
                is UiListState.Loading -> {
                    removeActionIcons()
                    adapter.applySkeleton()
                    clearTonOnlySubtitleAction()
                    headerView.setSubtitle(Localization.updating)
                }
                is UiListState.Empty -> {
                    if (viewModel.hasNfts) {
                        applyActionIcons()
                    } else {
                        removeActionIcons()
                    }
                    refreshView.isRefreshing = false
                    setEmptyState()
                    setupTonOnlySubtitle()
                }
                is UiListState.Items -> {
                    applyActionIcons()
                    setListState()
                    setupTonOnlySubtitle()
                    adapter.submitList(state.items) {
                        if (!state.cache) {
                            refreshView.isRefreshing = false
                        }
                    }
                }
            }
        }
    }

    private fun setupHeaderLayout() {
        headerView.title = getString(Localization.collectibles)
        headerView.setIcon(UIKitIcon.ic_chevron_left_16)
        headerView.doOnCloseClick = { finish() }
        headerView.titleView.gravity = Gravity.CENTER
        headerView.subtitleView.gravity = Gravity.CENTER
        headerView.findViewById<View>(uikit.R.id.header_text).updatePadding(left = 0, right = 0)
        headerView.findViewById<View>(uikit.R.id.subtitle_container).updateLayoutParams<LinearLayoutCompat.LayoutParams> {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        (headerView.findViewById<View>(uikit.R.id.subtitle_container) as LinearLayoutCompat).gravity =
            Gravity.CENTER_VERTICAL
        if (requireContext().isLightTheme) {
            headerView.setColor(requireContext().backgroundPageColor)
        } else {
            headerView.setColor(requireContext().backgroundTransparentColor)
        }
    }

    private fun setupTonOnlySubtitle() {
        removeExtraSubtitleViews()
        headerView.setSubtitle(getString(Localization.collectibles_ton_only_subtitle))
        val subtitleView = headerView.subtitleView
        val icon = headerView.getDrawable(UIKitIcon.ic_information_circle_12).mutate().also {
            DrawableCompat.setTint(it, requireContext().textSecondaryColor)
        }
        subtitleView.setRightDrawable(icon)
        subtitleView.compoundDrawablePadding = 4.dp
        subtitleView.isClickable = true
        subtitleView.setOnClickListener { showTonOnlyInfoDialog() }
    }

    private fun clearTonOnlySubtitleAction() {
        headerView.subtitleView.apply {
            clearDrawables()
            setOnClickListener(null)
            isClickable = false
        }
    }

    private fun removeExtraSubtitleViews() {
        val container = headerView.findViewById<LinearLayoutCompat>(uikit.R.id.subtitle_container)
        val loaderView = headerView.findViewById<View>(uikit.R.id.header_loader)
        for (index in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(index)
            if (child != headerView.subtitleView && child != loaderView) {
                container.removeViewAt(index)
            }
        }
    }

    private fun showTonOnlyInfoDialog() {
        tonOnlyInfoDialog.show()
    }

    private fun applyActionIcons() {
        val wallet = viewModel.walletFlow.value ?: return
        headerView.setAction(UIKitIcon.ic_sliders_16)
        headerView.doOnActionClick = {
            navigation?.add(CollectiblesManageScreen.newInstance(wallet))
        }
    }

    private fun removeActionIcons() {
        headerView.setAction(0)
        headerView.doOnActionClick = null
    }

    private fun openQRCode() {
        navigation?.add(QrAssetFragment.newInstance())
    }

    private fun setEmptyState() {
        if (emptyView.visibility == View.VISIBLE) {
            return
        }
        emptyView.visibility = View.VISIBLE
        listView.visibility = View.GONE
    }

    private fun setListState() {
        if (listView.visibility == View.VISIBLE) {
            return
        }
        emptyView.visibility = View.GONE
        listView.visibility = View.VISIBLE
    }

    companion object {

        private const val ARG_FROM = "from"

        fun newInstance(from: String = "unknown"): CollectiblesScreen {
            val screen = CollectiblesScreen()
            screen.putStringArg(ARG_FROM, from)
            return screen
        }
    }
}