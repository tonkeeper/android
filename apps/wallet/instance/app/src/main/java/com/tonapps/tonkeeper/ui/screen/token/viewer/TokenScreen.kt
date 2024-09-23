package com.tonapps.tonkeeper.ui.screen.token.viewer

import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.core.history.list.HistoryAdapter
import com.tonapps.tonkeeper.core.history.list.HistoryItemDecoration
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.popup.ActionSheet
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.token.unverified.TokenUnverifiedScreen
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.TokenAdapter
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.ListPaginationListener
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.localization.Localization
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.dp
import uikit.extensions.drawable
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setRightDrawable

class TokenScreen(wallet: WalletEntity): BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.SwipeBack {

    private val args: TokenArgs by lazy { TokenArgs(requireArguments()) }

    override val viewModel: TokenViewModel by walletViewModel { parametersOf(args.address) }

    private val tokenAdapter = TokenAdapter()
    private val historyAdapter = HistoryAdapter()
    private val paginationListener = object : ListPaginationListener() {
        override fun onLoadMore() {
            viewModel.loadMore()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val padding = requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium)
        setListPadding(0, padding, 0, padding)
        setTitle(args.symbol)
        setAdapter(ConcatAdapter(tokenAdapter, historyAdapter))
        addItemDecoration(HistoryItemDecoration())
        addItemDecoration(object : RecyclerView.ItemDecoration() {

            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val position = parent.getChildAdapterPosition(view)
                if (parent.isLayoutRequested) {
                    parent.doOnLayout { getItemOffsets(outRect, view, parent, state) }
                    return
                }
                if (position == 0) {
                    return
                }
                val holder = parent.findViewHolderForAdapterPosition(position) ?: return
                val item = (holder as? BaseListHolder<*>)?.item ?: return
                if (item is HistoryItem) {
                    outRect.left = padding
                    outRect.right = padding
                }
            }
        })
        addScrollListener(paginationListener)

        collectFlow(viewModel.tokenFlow, ::applyToken)
        collectFlow(viewModel.uiItemsFlow, tokenAdapter::submitList)
        collectFlow(viewModel.uiHistoryFlow, historyAdapter::submitList)
    }

    private fun applyToken(token: AccountTokenEntity) {
        setActionIcon(R.drawable.ic_ellipsis_16) { actionMenu(it, token) }
        if (!token.verified) {
            applyUnverifiedToken()
        }
    }

    private fun applyUnverifiedToken() {
        val color = requireContext().accentOrangeColor
        val icon = requireContext().drawable(UIKitIcon.ic_information_circle_16).apply {
            setTint(color)
        }

        headerView.setSubtitle(Localization.unverified_token)
        headerView.subtitleView.setTextColor(color)
        headerView.subtitleView.compoundDrawablePadding = 8.dp
        headerView.subtitleView.setRightDrawable(icon)
        headerView.setOnClickListener { navigation?.add(TokenUnverifiedScreen.newInstance()) }
    }

    private fun actionMenu(view: View, token: AccountTokenEntity) {
        val detailsUrl = if (token.isTon) {
            Uri.parse("https://tonviewer.com/${screenContext.wallet.address}")
        } else {
            Uri.parse("https://tonviewer.com/${screenContext.wallet.address}/jetton/${token.address}")
        }

        val actionSheet = ActionSheet(requireContext())
        actionSheet.addItem(0, Localization.view_details, R.drawable.ic_globe_16)
        actionSheet.doOnItemClick = { navigation?.openURL(detailsUrl.toString()) }
        actionSheet.show(view)
    }

    companion object {
        fun newInstance(
            wallet: WalletEntity,
            address: String,
            name: String,
            symbol: String
        ): TokenScreen {
            val fragment = TokenScreen(wallet)
            fragment.setArgs(TokenArgs(address, name, symbol))
            return fragment
        }
    }
}