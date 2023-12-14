package com.tonkeeper.core.history.list

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.core.history.list.holder.HistoryActionHolder
import com.tonkeeper.core.history.list.item.HistoryActionItem
import com.tonkeeper.core.history.list.item.HistoryHeaderItem
import uikit.R
import uikit.extensions.getDimensionPixelSize
import uikit.list.ListCell

class HistoryItemDecoration(
    context: Context
): RecyclerView.ItemDecoration() {

    private val offset = context.getDimensionPixelSize(R.dimen.offsetMedium)

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)
        val adapter = parent.adapter as? HistoryAdapter ?: return
        val position = parent.getChildAdapterPosition(view)
        val item = adapter.getItem(position)
        if (item is HistoryActionItem) {
            if (item.position == ListCell.Position.SINGLE || item.position == ListCell.Position.FIRST) {
                outRect.top += offset
            }
        } else if (item is HistoryHeaderItem) {
            outRect.top += offset
        }
    }

}