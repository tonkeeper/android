package com.tonapps.tonkeeper.ui.screen.init.pager

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.tonapps.tonkeeper.ui.screen.init.pager.child.phrase.PhraseChild
import com.tonapps.tonkeeper.ui.screen.init.pager.child.NameChild
import com.tonapps.tonkeeper.ui.screen.init.pager.child.PasscodeChild
import com.tonapps.tonkeeper.ui.screen.init.pager.child.SignerChild
import com.tonapps.tonkeeper.ui.screen.init.pager.child.watch.WatchChild

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
            ChildPageType.Name -> NameChild.newInstance(name)
            ChildPageType.Import -> PhraseChild.newInstance(false)
            ChildPageType.Watch -> WatchChild.newInstance()
            ChildPageType.ImportTestnet -> PhraseChild.newInstance(true)
            ChildPageType.Signer -> SignerChild.newInstance()
        }
    }
}
