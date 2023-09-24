package com.tonkeeper

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.tonkeeper.fragment.currency.CurrencyScreen
import com.tonkeeper.uikit.base.BaseActivity
import com.tonkeeper.uikit.base.fragment.BaseFragment
import com.tonkeeper.uikit.navigation.Navigation
import com.tonkeeper.fragment.intro.IntroFragment
import com.tonkeeper.fragment.main.MainFragment
import kotlinx.coroutines.launch

class MainActivity: BaseActivity(), Navigation {

    companion object {
        private val hostFragmentId = R.id.nav_host_fragment
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            init()
        }
    }

    private fun init() {
        lifecycleScope.launch {
            val wallet = App.walletManager.getWalletInfo()
            if (wallet == null) {
                replace(IntroFragment.newInstance(), false)
            } else {
                replace(MainFragment.newInstance(), false)
            }
        }
    }

    override fun replace(fragment: BaseFragment, addToBackStack: Boolean) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(hostFragmentId, fragment)
        if (addToBackStack) {
            transaction.addToBackStack(null)
        }
        transaction.commit()
    }

    override fun add(fragment: BaseFragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.add(hostFragmentId, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    override fun back() {
        onBackPressed()
    }
}