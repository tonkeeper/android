package com.tonapps.tonkeeper.ui.screen.token.viewer.paging

import android.view.View
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.tonapps.deposit.screens.send.state.SendFee
import com.tonapps.tonkeeper.core.history.list.holder.HistoryActionHolder
import com.tonapps.tonkeeper.core.history.list.holder.HistoryHeaderHolder
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class HistoryPagingAdapter(
    private val disableOpenAction: Boolean = false,
    private val shouldShowFeeToggle: () -> Boolean = { true },
    private val showFeeMethods: (currentFee: SendFee, targetView: View) -> Unit = { _, _ -> }
) : PagingDataAdapter<HistoryItem, BaseListHolder<out BaseListItem>>(DIFF) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseListHolder<out BaseListItem> {
        return when (viewType) {
            HistoryItem.TYPE_ACTION -> HistoryActionHolder(
                parent,
                disableOpenAction,
                shouldShowFeeToggle,
                showFeeMethods
            )

            HistoryItem.TYPE_HEADER -> HistoryHeaderHolder(parent)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: BaseListHolder<out BaseListItem>, position: Int) {
        val item = getItem(position) ?: return
        holder.bind(item)
    }

    override fun getItemViewType(position: Int): Int {
        return getItem(position)?.type ?: HistoryItem.TYPE_ACTION
    }

    override fun onViewRecycled(holder: BaseListHolder<out BaseListItem>) {
        holder.unbind()
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<HistoryItem>() {
            override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
                return oldItem.uniqueId == newItem.uniqueId
            }

            override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
