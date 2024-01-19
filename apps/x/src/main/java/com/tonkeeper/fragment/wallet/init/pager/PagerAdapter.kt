package com.tonkeeper.fragment.wallet.init.pager

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.tonkeeper.fragment.wallet.init.pager.child.ImportChild
import com.tonkeeper.fragment.wallet.init.pager.child.NameChild
import com.tonkeeper.fragment.wallet.init.pager.child.PasscodeChild
import com.tonkeeper.fragment.wallet.init.pager.child.PushChild
import com.tonkeeper.fragment.wallet.init.pager.child.watch.WatchChild

class PagerAdapter(
    fragment: Fragment,
    private val pages: List<ChildPageType>,
    private val name: String?
): FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int {
        return pages.size
    }

    override fun createFragment(position: Int): Fragment {
        return when (pages[position]) {
            ChildPageType.Passcode -> PasscodeChild.newInstance()
            ChildPageType.Push -> PushChild.newInstance()
            ChildPageType.Name -> NameChild.newInstance(name)
            ChildPageType.Import -> ImportChild.newInstance(false)
            ChildPageType.Watch -> WatchChild.newInstance()
            ChildPageType.ImportTestnet -> ImportChild.newInstance(true)
        }
    }
}
