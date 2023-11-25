package com.tonkeeper.core.history.list

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import uikit.R
import uikit.extensions.getDimensionPixelSize

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
        outRect.bottom += offset
    }

}