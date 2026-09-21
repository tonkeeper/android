package com.tonapps.tonkeeper.ui.screen.wallet.manage

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.wallet.manage.list.Adapter
import com.tonapps.tonkeeper.ui.screen.wallet.manage.list.Item
import com.tonapps.tonkeeper.ui.screen.wallet.manage.list.holder.Holder
import com.tonapps.tonkeeper.ui.screen.wallet.manage.list.holder.TokenHolder
import com.tonapps.wallet.localization.Localization
import uikit.HapticHelper
import uikit.R
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize

class TokensManageScreen(wallet: WalletEntity): BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.BottomSheet {

    override val fragmentName: String = "TokensManageScreen"

    override val viewModel: TokensManageViewModel by walletViewModel()

    private val adapter: Adapter by lazy {
        Adapter(viewModel::onPinChange, viewModel::onHiddenChange, ::onDrag)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    private fun onDrag(holder: TokenHolder) {
        getTouchHelper()?.startDrag(holder)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(getString(Localization.home_screen))
        applyListMargin(top = requireContext().getDimensionPixelSize(R.dimen.barHeight))
        setAdapter(adapter)
        val horizontalOffset = requireContext().getDimensionPixelSize(uikit.R.dimen.cornerMedium)
        setListPadding(horizontalOffset, 0, horizontalOffset, 0)
        setTouchHelperCallback(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

            override fun isLongPressDragEnabled() = false

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val item = (viewHolder as? Holder<*>)?.item ?: return false
                if (item is Item.Token && item.pinned) {
                    val fromPosition = viewHolder.bindingAdapterPosition
                    val toPosition = target.bindingAdapterPosition

                    if (!adapter.moveItem(fromPosition, toPosition)) {
                        return false
                    }

                    HapticHelper.impactLight(requireContext())
                    return true
                }
                return false
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                val item = (viewHolder as? Holder<*>)?.item ?: return
                if (item is Item.Token && item.pinned) {
                    viewModel.saveOrder(adapter.currentList)
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }

        })
    }

    companion object {
        fun newInstance(wallet: WalletEntity) = TokensManageScreen(wallet)
    }
}