package com.tonapps.tonkeeper.fragment.send.pager

import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.tonapps.tonkeeper.fragment.send.amount.AmountScreen
import com.tonapps.tonkeeper.fragment.send.confirm.ConfirmScreen
import com.tonapps.tonkeeper.fragment.send.recipient.RecipientScreen

class SendScreenAdapter(
    private val parentFragment: Fragment,
    private val items: List<Item>
): FragmentStateAdapter(parentFragment) {

    enum class Item {
        Recipient, Amount, Confirm
    }

    val recipientScreen: RecipientScreen?
        get() = findFragment(Item.Recipient) as? RecipientScreen

    val amountScreen: AmountScreen?
        get() = findFragment(Item.Amount) as? AmountScreen

    val confirmScreen: ConfirmScreen?
        get() = findFragment(Item.Confirm) as? ConfirmScreen

    override fun getItemCount() = items.size

    fun findFragmentByPosition(position: Int): Fragment? {
        return findFragment(items[position])
    }

    fun findFragment(item: Item): Fragment? {
        val fragments = parentFragment.childFragmentManager.fragments
        return fragments.find {
            it is RecipientScreen && item == Item.Recipient ||
            it is AmountScreen && item == Item.Amount ||
            it is ConfirmScreen && item == Item.Confirm
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun createFragment(position: Int): Fragment {
        return when (items[position]) {
            Item.Recipient -> RecipientScreen.newInstance()
            Item.Amount -> AmountScreen.newInstance()
            Item.Confirm -> ConfirmScreen.newInstance()
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.isNestedScrollingEnabled = true
    }
}