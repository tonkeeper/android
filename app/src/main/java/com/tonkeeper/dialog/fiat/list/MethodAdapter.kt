package com.tonkeeper.dialog.fiat.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.core.fiat.models.FiatItem
import uikit.list.BaseListHolder
import uikit.list.BaseListItem
import uikit.list.BaseListAdapter
import uikit.list.ListCell

class MethodAdapter(
    private val onClick: (item: MethodItem) -> Unit
): BaseListAdapter() {

    companion object {
        fun buildMethodItems(list: List<FiatItem>): List<MethodItem> {
            val items = mutableListOf<MethodItem>()
            for ((index, item) in list.withIndex()) {
                val position = ListCell.getPosition(list.size, index)
                items.add(MethodItem(item, position))
            }
            return items
        }
    }

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return MethodHolder(parent, onClick)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.setHasFixedSize(false)
    }
}