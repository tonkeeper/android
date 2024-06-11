package com.tonapps.tonkeeper.dialog.fiat.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.core.fiat.models.FiatItem

class MethodAdapter(
    private val onClick: (item: MethodItem) -> Unit
): com.tonapps.uikit.list.BaseListAdapter() {

    companion object {
        fun buildMethodItems(list: List<FiatItem>): List<MethodItem> {
            val items = mutableListOf<MethodItem>()
            for ((index, item) in list.withIndex()) {
                val position = com.tonapps.uikit.list.ListCell.getPosition(list.size, index)
                items.add(MethodItem(item, position))
            }
            return items
        }
    }

    fun submit(items: List<FiatItem>, commitCallback: Runnable? = null) {
        submitList(buildMethodItems(items), commitCallback)
    }

    override fun createHolder(parent: ViewGroup, viewType: Int): com.tonapps.uikit.list.BaseListHolder<out com.tonapps.uikit.list.BaseListItem> {
        return MethodHolder(parent, onClick)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.setHasFixedSize(false)
    }
}