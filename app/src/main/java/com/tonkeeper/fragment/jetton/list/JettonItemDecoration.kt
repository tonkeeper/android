package com.tonkeeper.fragment.jetton.list

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.core.history.list.holder.HistoryHolder
import com.tonkeeper.fragment.jetton.list.holder.JettonHeaderHolder
import uikit.R
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.horizontal

class JettonItemDecoration(
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
        val holder = parent.getChildViewHolder(view)
        if (holder is HistoryHolder<*>) {
            outRect.horizontal = offset
        }
    }
}