package uikit.list

import androidx.recyclerview.widget.DiffUtil

class DiffCallback: DiffUtil.ItemCallback<BaseListItem>() {

    companion object {
        fun create() = DiffCallback()
    }

    override fun areItemsTheSame(oldItem: BaseListItem, newItem: BaseListItem): Boolean {
        return oldItem.type == newItem.type
    }

    override fun areContentsTheSame(oldItem: BaseListItem, newItem: BaseListItem): Boolean {
        return newItem == oldItem
    }
}