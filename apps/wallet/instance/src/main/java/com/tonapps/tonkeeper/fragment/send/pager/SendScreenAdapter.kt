package com.tonapps.tonkeeper.fragment.send.pager

import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.tonapps.tonkeeper.fragment.send.amount.AmountScreen
import com.tonapps.tonkeeper.fragment.send.confirm.ConfirmScreen
import com.tonapps.tonkeeper.fragment.send.recipient.RecipientScreen

class SendScreenAdapter(
    private val fragment: Fragment
): FragmentStateAdapter(fragment) {

    private companion object {

        private var COUNT = 0

        private val POSITION_RECIPIENT = COUNT++
        private val POSITION_AMOUNT = COUNT++
        private val POSITION_CONFIRM = COUNT++
    }

    val recipientScreen: RecipientScreen?
        get() = findFragmentByPosition(POSITION_RECIPIENT) as? RecipientScreen

    val amountScreen: AmountScreen?
        get() = findFragmentByPosition(POSITION_AMOUNT) as? AmountScreen

    val confirmScreen: ConfirmScreen?
        get() = findFragmentByPosition(POSITION_CONFIRM) as? ConfirmScreen

    override fun getItemCount(): Int {
        return COUNT
    }

    fun findFragmentByPosition(position: Int): Fragment? {
        return fragment.childFragmentManager.findFragmentByTag("f$position")
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            POSITION_RECIPIENT -> RecipientScreen.newInstance()
            POSITION_AMOUNT -> AmountScreen.newInstance()
            POSITION_CONFIRM -> ConfirmScreen.newInstance()
            else -> throw IllegalStateException("Unknown position: $position")
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.isNestedScrollingEnabled = true
    }
}