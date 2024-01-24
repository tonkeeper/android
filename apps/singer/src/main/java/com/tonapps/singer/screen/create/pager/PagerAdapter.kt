package com.tonapps.singer.screen.create.pager

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.tonapps.singer.screen.create.child.CreateNameFragment
import com.tonapps.singer.screen.create.child.CreatePasswordFragment
import com.tonapps.singer.screen.create.child.CreatePhraseFragment

class PagerAdapter(
    fragment: Fragment,
    private val pages: List<PageType>
): FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int {
        return pages.size
    }

    override fun createFragment(position: Int): Fragment {
        return when(pages[position]) {
            PageType.Password -> CreatePasswordFragment.newInstance(false)
            PageType.RepeatPassword -> CreatePasswordFragment.newInstance(true)
            PageType.Name -> CreateNameFragment.newInstance()
            PageType.Phrase -> CreatePhraseFragment.newInstance()
        }
    }

}