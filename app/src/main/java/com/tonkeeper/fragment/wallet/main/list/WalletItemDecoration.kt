package com.tonkeeper.fragment.wallet.main.list

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.fragment.wallet.main.list.item.WalletCellItem
import com.tonkeeper.fragment.wallet.main.pager.WalletScreenAdapter
import uikit.extensions.dp
import uikit.extensions.getDimensionPixelSize
import uikit.list.ListCell

class WalletItemDecoration(
    context: Context,
    private val spanCount: Int,
): RecyclerView.ItemDecoration() {

    private val edgeSpacing = 12.dp

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)
        val adapter = parent.adapter as? WalletContentAdapter ?: return

        val position = parent.getChildAdapterPosition(view)
        val item = adapter.get(position)
        if (item is WalletCellItem) {
            getCellItemOffsets(outRect, item)
            return
        }
    }

    private fun getCellItemOffsets(outRect: Rect, item: WalletCellItem) {
        outRect.left = 4.dp
        outRect.right = 4.dp
        when (item.position) {
            ListCell.Position.SINGLE -> {
                outRect.top = edgeSpacing
                outRect.bottom = edgeSpacing
            }
            ListCell.Position.FIRST -> {
                outRect.top = edgeSpacing
                outRect.bottom = 0
            }
            ListCell.Position.LAST -> {
                outRect.top = 0
                outRect.bottom = edgeSpacing
            }
            ListCell.Position.MIDDLE -> {
                outRect.top = 0
                outRect.bottom = 0
            }
        }

    }
}