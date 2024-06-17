package com.tonapps.tonkeeper.ui.screen.wallet.picker.list

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.holder.AddHolder
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.holder.Holder
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.holder.SkeletonHolder
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.holder.WalletHolder
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.entities.WalletEntity

class WalletPickerAdapter: RecyclerView.Adapter<Holder<*>>() {

    companion object {

        private val defaultItems: List<Item> = listOf(
            Item.Skeleton(ListCell.Position.SINGLE),
            Item.AddWallet
        )

        fun map(
            wallets: List<WalletEntity>,
            activeWallet: WalletEntity,
            balances: List<CharSequence>,
            hiddenBalance: Boolean
        ): List<Item> {
            val uiItems = mutableListOf<Item>()
            for ((index, wallet) in wallets.withIndex()) {
                val item = Item.Wallet(
                    accountId = wallet.accountId,
                    walletId = wallet.id,
                    walletLabel = wallet.label,
                    walletType = wallet.type,
                    selected = wallet.id == activeWallet.id,
                    position = ListCell.getPosition(wallets.size, index),
                    balance = balances[index],
                    hiddenBalance = hiddenBalance
                )
                uiItems.add(item)
            }
            uiItems.add(Item.AddWallet)
            return uiItems
        }
    }

    private var list = defaultItems.toList()

    val currentList: List<Item>
        get() = list

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

    fun rebuild(): List<String> {
        var wallets = list.filterIsInstance<Item.Wallet>()
        wallets = wallets.mapIndexed { index, it ->
            it.copy(position = ListCell.getPosition(wallets.size, index))
        }
        val uiItems = mutableListOf<Item>()
        uiItems.addAll(wallets)
        uiItems.add(Item.AddWallet)
        submitList(uiItems)
        return wallets.map { it.walletId }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder<*> {
        return when(viewType) {
            Item.TYPE_WALLET -> WalletHolder(parent)
            Item.TYPE_ADD_WALLET -> AddHolder(parent)
            Item.TYPE_SKELETON -> SkeletonHolder(parent)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: Holder<*>, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.setHasFixedSize(false)
        recyclerView.isNestedScrollingEnabled = true
        recyclerView.itemAnimator = null
        recyclerView.layoutAnimation = null
    }
}