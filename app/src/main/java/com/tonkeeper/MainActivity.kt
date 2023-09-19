package com.tonkeeper

import android.os.Bundle
import com.tonkeeper.uikit.base.BaseActivity
import com.tonkeeper.uikit.base.BaseFragment
import com.tonkeeper.fragment.intro.IntroFragment
import com.tonkeeper.fragment.Navigation

class MainActivity: BaseActivity(), Navigation {

    companion object {
        private val hostFragmentId = R.id.nav_host_fragment
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            replace(IntroFragment.newInstance())
        }
    }

    override fun replace(fragment: BaseFragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(hostFragmentId, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }
}