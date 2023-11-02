package com.tonkeeper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.tonkeeper.fragment.intro.IntroFragment
import uikit.base.BaseActivity
import uikit.base.fragment.BaseFragment
import uikit.navigation.Navigation
import com.tonkeeper.fragment.main.MainFragment
import com.tonkeeper.fragment.passcode.PasscodeScreen
import com.tonkeeper.fragment.web.WebFragment
import com.tonkeeper.tonconnect.TonConnect
import kotlinx.coroutines.launch

class MainActivity: BaseActivity(), Navigation {

    companion object {
        private val hostFragmentId = R.id.nav_host_fragment
    }

    private val tonConnect: TonConnect by lazy {
        TonConnect(this, lifecycleScope)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            init(false)
        }
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data ?: return
        handleUri(data)
    }

    private fun handleUri(uri: Uri) {
        if (tonConnect.isSupportUri(uri)) {
            tonConnect.processUri(uri)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun init(skipPasscode: Boolean) {
        lifecycleScope.launch {
            val wallet = App.walletManager.getWalletInfo()
            if (wallet == null) {
                replace(IntroFragment.newInstance(), false)
            } else if (!skipPasscode && App.settings.lockScreen) {
                replace(PasscodeScreen.newInstance(), false)
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
        transaction.setReorderingAllowed(true)
        transaction.add(hostFragmentId, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    override fun remove(fragment: BaseFragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.remove(fragment)
        transaction.commit()
    }

    override fun openURL(url: String) {
        add(WebFragment.newInstance(url))
    }

}