package com.tonkeeper.ui.list.wallet.decoration

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tonkeeper.R
import com.tonkeeper.extensions.getDimension
import com.tonkeeper.ui.list.wallet.WalletAdapter
import com.tonkeeper.ui.list.wallet.item.WalletCellItem

class WalletItemDecoration(
    context: Context,
    private val spanCount: Int,
): RecyclerView.ItemDecoration() {

    private val edgeSpacing = context.getDimension(R.dimen.offset).toInt()
    private val nftSpacing = edgeSpacing / 2

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)
        val adapter = parent.adapter as? WalletAdapter ?: return

        val position = parent.getChildAdapterPosition(view)
        val item = adapter.get(position)
        if (item is WalletCellItem) {
            getCellItemOffsets(outRect, item)
            return
        }

        val column = position % spanCount
        outRect.left = column * nftSpacing / spanCount
        outRect.right = nftSpacing - (column + 1) * nftSpacing / spanCount
        if (position >= spanCount) {
            outRect.top = nftSpacing
        }
    }

    private fun getCellItemOffsets(outRect: Rect, item: WalletCellItem) {
        when (item.position) {
            WalletCellItem.Position.SINGLE -> {
                outRect.top = edgeSpacing
                outRect.bottom = edgeSpacing
            }
            WalletCellItem.Position.FIRST -> {
                outRect.top = edgeSpacing
                outRect.bottom = 0
            }
            WalletCellItem.Position.LAST -> {
                outRect.top = 0
                outRect.bottom = edgeSpacing
            }
            WalletCellItem.Position.MIDDLE -> {
                outRect.top = 0
                outRect.bottom = 0
            }
        }

    }
}