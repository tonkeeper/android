package com.tonapps.tonkeeper.ui.screen.wallet.manage

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.wallet.manage.list.Adapter
import com.tonapps.tonkeeper.ui.screen.wallet.manage.list.Item
import com.tonapps.tonkeeper.ui.screen.wallet.manage.list.holder.Holder
import com.tonapps.tonkeeper.ui.screen.wallet.manage.list.holder.TokenHolder
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.HapticHelper
import uikit.base.BaseFragment
import uikit.base.BaseListFragment
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize

class TokensManageScreen(wallet: WalletEntity): BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.BottomSheet {

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
                    adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
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
                    viewModel.changeOrder(item.address, viewHolder.bindingAdapterPosition)
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }

        })
    }

    companion object {
        fun newInstance(wallet: WalletEntity) = TokensManageScreen(wallet)
    }
}