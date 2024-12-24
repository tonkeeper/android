package com.tonapps.tonkeeper.ui.screen.settings.theme

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.base.BaseListWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.settings.theme.list.Adapter
import com.tonapps.tonkeeper.ui.screen.settings.theme.list.Item
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.dp

class ThemeScreen(wallet: WalletEntity): BaseListWalletScreen<ScreenContext.Wallet>(ScreenContext.Wallet(wallet)), BaseFragment.SwipeBack {

    override val fragmentName: String = "ThemeScreen"

    override val viewModel: ThemeViewModel by viewModel()

    private val adapter = Adapter { item ->
        viewModel.setTheme(item.theme.resId)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(getString(Localization.appearance))

        setAdapter(adapter)
        addItemDecoration(object : RecyclerView.ItemDecoration() {

            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val position = parent.getChildAdapterPosition(view)
                val item = adapter.getItem(position)
                if (item is Item.Icon) {
                    if (position == 6) {
                        outRect.set(0, 0, 0, 0)
                    } else {
                        outRect.set(6.dp, 0, 0, 0)
                    }
                } else {
                    outRect.set(0, 0, 0, 0)
                }
            }
        })
        setLayoutManager(object : GridLayoutManager(context, 4) {
            init {
                spanSizeLookup = object : SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return when (adapter.getItemViewType(position)) {
                            Item.TYPE_ICON -> 1
                            else -> 4
                        }
                    }
                }
            }

            override fun supportsPredictiveItemAnimations(): Boolean = false
        })

        collectFlow(viewModel.uiItemsFlow, adapter::submitList)
    }

    companion object {
        fun newInstance(wallet: WalletEntity) = ThemeScreen(wallet)
    }
}