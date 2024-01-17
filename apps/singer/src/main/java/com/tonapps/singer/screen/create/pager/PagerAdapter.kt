package com.tonapps.singer.screen.create.pager

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.tonapps.singer.screen.create.CreateNameFragment
import com.tonapps.singer.screen.create.password.CreatePasswordFragment
import com.tonapps.singer.screen.create.password.RepeatPasswordFragment
import com.tonapps.singer.screen.password.PasswordFragment

class PagerAdapter(
    fragment: Fragment,
    private val pages: List<PageType>
): FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int {
        return pages.size
    }

    override fun createFragment(position: Int): Fragment {
        return when(pages[position]) {
            PageType.Password -> CreatePasswordFragment.newInstance()
            PageType.RepeatPassword -> RepeatPasswordFragment.newInstance()
            PageType.Name -> CreateNameFragment.newInstance()
        }
    }

}