package com.tonapps.tonkeeper.ui.screen.wallet.manage.list

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.wallet.manage.list.holder.Holder
import com.tonapps.tonkeeper.ui.screen.wallet.manage.list.holder.SpaceHolder
import com.tonapps.tonkeeper.ui.screen.wallet.manage.list.holder.TitleHolder
import com.tonapps.tonkeeper.ui.screen.wallet.manage.list.holder.TokenHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.DiffCallback

class Adapter(
    private val doOnPinChange: (tokenAddress: String, pin: Boolean) -> Unit,
    private val doOnHiddeChange: (tokenAddress: String, hidden: Boolean) -> Unit
): RecyclerView.Adapter<Holder<*>>() {

    private var list = listOf<Item>()

    private fun getItem(position: Int) = list[position]

    override fun getItemViewType(position: Int) = list[position].type

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newList: List<Item>) {
        this.list = newList
        notifyDataSetChanged()
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        val newList = list.toMutableList()
        val item = newList.removeAt(fromPosition)
        newList.add(toPosition, item)
        list = newList
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder<*> {
        return when(viewType) {
            Item.TYPE_TITLE -> TitleHolder(parent)
            Item.TYPE_TOKEN -> TokenHolder(parent, doOnPinChange, doOnHiddeChange)
            Item.TYPE_SPACE -> SpaceHolder(parent)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: Holder<*>, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.setHasFixedSize(true)
        recyclerView.isNestedScrollingEnabled = true
        recyclerView.itemAnimator = null
        recyclerView.layoutAnimation = null
    }
}