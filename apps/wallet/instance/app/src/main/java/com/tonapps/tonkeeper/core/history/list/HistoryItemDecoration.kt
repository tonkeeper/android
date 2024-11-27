package com.tonapps.tonkeeper.core.history.list

import android.graphics.Rect
import android.util.Log
import android.view.View
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.ListCell
import uikit.extensions.dp

class HistoryItemDecoration: RecyclerView.ItemDecoration() {

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

        val holder = findViewHolder(parent, position) ?: return
        val item = (holder as? BaseListHolder<*>)?.item ?: return

        if (item is HistoryItem.Event && (item.position == ListCell.Position.LAST || item.position == ListCell.Position.SINGLE)) {
            outRect.bottom = 6.dp
        } else if (item is HistoryItem.App) {
            outRect.bottom = 6.dp
        }
    }

    private fun findViewHolder(parent: RecyclerView, position: Int): RecyclerView.ViewHolder? {
        return parent.findViewHolderForLayoutPosition(position) ?: parent.findViewHolderForAdapterPosition(position)
    }

}