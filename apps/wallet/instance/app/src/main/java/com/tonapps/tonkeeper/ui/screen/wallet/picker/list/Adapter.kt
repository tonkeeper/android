package com.tonapps.tonkeeper.ui.screen.wallet.picker.list

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.collection.ArrayMap
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.extensions.isUIThread
import com.tonapps.extensions.putEnum
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.manager.assets.WalletBalanceEntity
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.holder.AddHolder
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.holder.Holder
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.holder.SkeletonHolder
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.holder.WalletHolder
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.localization.Localization
import java.util.Collections

class Adapter(
    private val onClick: (WalletEntity) -> Unit
): RecyclerView.Adapter<Holder<*>>() {

    companion object {

        private val defaultItems: List<Item> = listOf(
            Item.Skeleton(ListCell.Position.SINGLE),
            Item.AddWallet
        )

        fun map(
            context: Context,
            wallets: List<WalletEntity>,
            activeWallet: WalletEntity,
            currency: WalletCurrency,
            balances: List<WalletBalanceEntity>,
            hiddenBalance: Boolean = false,
            walletIdFocus: String = "",
        ): List<Item> {
            val uiItems = mutableListOf<Item>()
            for ((index, wallet) in wallets.withIndex()) {
                val balance = balances.find {
                    it.accountId.equalsAddress(wallet.accountId) && it.testnet == wallet.testnet
                }

                val balanceFormat = balance?.balance?.let {
                    CurrencyFormatter.formatFiat(
                        currency = if (wallet.testnet) WalletCurrency.TON.code else currency.code,
                        value = it
                    )
                } ?: context.getString(Localization.loading)

                val item = Item.Wallet(
                    selected = wallet.id == activeWallet.id,
                    position = ListCell.getPosition(wallets.size, index),
                    balance = balanceFormat,
                    hiddenBalance = hiddenBalance,
                    wallet = wallet.copy(),
                    focusAnimation = walletIdFocus == wallet.id
                )
                uiItems.add(item)
            }
            uiItems.add(Item.AddWallet)
            return uiItems.toList()
        }
    }

    private class DiffCallback(
        private val oldList: List<Item>,
        private val newList: List<Item>
    ): DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            if (oldItem is Item.Wallet && newItem is Item.Wallet) {
                return Bundle().apply {
                    if (oldItem.balance != newItem.balance) putCharSequence("balance", newItem.balance)
                    if (oldItem.selected != newItem.selected) putBoolean("selected", newItem.selected)
                    if (oldItem.hiddenBalance != newItem.hiddenBalance) putBoolean("hiddenBalance", newItem.hiddenBalance)
                    if (oldItem.editMode != newItem.editMode) putBoolean("editMode", newItem.editMode)
                    if (oldItem.position != newItem.position) putInt("position", newItem.position.value)
                    if (oldItem.focusAnimation != newItem.focusAnimation) putBoolean("focusAnimation", newItem.focusAnimation)
                }
            }
            return null
        }
    }

    private var list = defaultItems.toList()

    val currentList: List<Item>
        get() = list

    fun getItem(position: Int) = list.getOrNull(position)

    override fun getItemViewType(position: Int) = getItem(position)?.type ?: Item.TYPE_SKELETON

    override fun getItemCount() = list.size

    override fun getItemId(position: Int) = getItem(position)?.id.hashCode().toLong()

    fun submitList(newList: List<Item>) {
        val diffResult = DiffUtil.calculateDiff(DiffCallback(list.toList(), newList))
        this.list = newList.toList()
        diffResult.dispatchUpdatesTo(this)
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        val wallets = list.filterIsInstance<Item.Wallet>()
        Collections.swap(wallets, fromPosition, toPosition)

        val uiItems = mutableListOf<Item>()
        uiItems.addAll(wallets.mapIndexed { index, it ->
            it.copy(position = ListCell.getPosition(wallets.size, index))
        })
        uiItems.add(Item.AddWallet)
        submitList(uiItems.toList())
    }

    fun rebuild(): List<String> {
        var wallets = list.filterIsInstance<Item.Wallet>()
        wallets = wallets.mapIndexed { index, it ->
            it.copy(position = ListCell.getPosition(wallets.size, index))
        }
        val uiItems = mutableListOf<Item>()
        uiItems.addAll(wallets)
        uiItems.add(Item.AddWallet)
        submitList(uiItems.toList())
        return wallets.map { it.walletId }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder<*> {
        return when(viewType) {
            Item.TYPE_WALLET -> WalletHolder(parent, onClick)
            Item.TYPE_ADD_WALLET -> AddHolder(parent)
            Item.TYPE_SKELETON -> SkeletonHolder(parent)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: Holder<*>, position: Int) {
        getItem(position)?.let {
            holder.bind(it)
        }
    }

    override fun onBindViewHolder(holder: Holder<*>, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty() && holder is WalletHolder) {
            val payload = payloads[0] as? Bundle ?: return
            val item = getItem(position) as? Item.Wallet ?: return

            if (payload.containsKey("balance") || payload.containsKey("hiddenBalance")) {
                holder.updateBalance(item)
            }
            if (payload.containsKey("selected")) {
                holder.updateSelected(item)
            }
            if (payload.containsKey("editMode")) {
                holder.updateEditMode(item)
            }
            if (payload.containsKey("position")) {
                holder.updatePosition(item)
            }
            if (payload.containsKey("focusAnimation")) {
                holder.updateFocusAnimation(item)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.setHasFixedSize(true)
        recyclerView.isNestedScrollingEnabled = true
        recyclerView.itemAnimator = null
        recyclerView.layoutAnimation = null
    }
}