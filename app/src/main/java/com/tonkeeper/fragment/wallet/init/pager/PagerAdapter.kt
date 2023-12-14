package com.tonkeeper.fragment.wallet.init.pager

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.tonkeeper.fragment.wallet.init.pager.child.ImportChild
import com.tonkeeper.fragment.wallet.init.pager.child.NameChild
import com.tonkeeper.fragment.wallet.init.pager.child.PasscodeChild
import com.tonkeeper.fragment.wallet.init.pager.child.PushChild

class PagerAdapter(
    fragment: Fragment,
    private val pages: List<ChildPageType>
): FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int {
        return pages.size
    }

    override fun createFragment(position: Int): Fragment {
        return when (pages[position]) {
            ChildPageType.Passcode -> PasscodeChild.newInstance()
            ChildPageType.Push -> PushChild.newInstance()
            ChildPageType.Name -> NameChild.newInstance()
            ChildPageType.Import -> ImportChild.newInstance()
        }
    }
}
