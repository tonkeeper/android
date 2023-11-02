package uikit.list

import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

abstract class DiffListAdapter(
    items: List<BaseListItem>
): ListAdapter<BaseListItem, BaseListHolder<out BaseListItem>>(DiffCallback.create()) {

    init {
        super.submitList(items)
    }

    abstract fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem>

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return createHolder(parent, viewType)
    }

    override fun onBindViewHolder(holder: BaseListHolder<out BaseListItem>, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    override fun getItemViewType(position: Int): Int {
        return getItem(position).type
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
    }
}