package com.tonkeeper

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.WindowCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import com.tonkeeper.core.Coin
import com.tonkeeper.core.PaymentURL
import com.tonkeeper.fragment.intro.IntroFragment
import uikit.base.BaseActivity
import uikit.base.fragment.BaseFragment
import uikit.navigation.Navigation
import com.tonkeeper.fragment.main.MainFragment
import com.tonkeeper.fragment.passcode.PasscodeScreen
import com.tonkeeper.fragment.web.WebFragment
import com.tonkeeper.core.tonconnect.TonConnect
import com.tonkeeper.fragment.camera.CameraFragment
import com.tonkeeper.fragment.send.SendScreen
import kotlinx.coroutines.launch
import uikit.navigation.Navigation.Companion.nav

class MainActivity: BaseActivity(), Navigation, ViewTreeObserver.OnPreDrawListener {

    companion object {
        private val hostFragmentId = R.id.nav_host_fragment
    }

    private lateinit var contentView: View
    private lateinit var tonConnect: TonConnect
    private var initialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        contentView = findViewById(android.R.id.content)
        contentView.viewTreeObserver.addOnPreDrawListener(this)

        init(false)

        handleIntent(intent)
        tonConnect = TonConnect(this)
        tonConnect.onCreate()
        initDebugVersion()
    }


    private fun initDebugVersion() {
        val debugVersionView = findViewById<AppCompatTextView>(R.id.debug_version)
        debugVersionView.text = BuildConfig.VERSION_NAME+" ("+BuildConfig.VERSION_CODE+")"
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data ?: return
        handleUri(data)
    }

    fun handleUri(uri: Uri) {
        if (tonConnect.isSupportUri(uri)) {
            tonConnect.processUri(uri)
        } else if (uri.scheme == "ton" || uri.host == "app.tonkeeper.com") {
            handleTON(uri)
        }
    }

    private fun handleTON(uri: Uri) {
        val paymentURL = PaymentURL(uri)

        if (paymentURL.action == PaymentURL.ACTION_TRANSFER) {
            val amount = Coin.toCoins(paymentURL.amount)
            val currentFragment = supportFragmentManager.findFragmentById(hostFragmentId)
            if (currentFragment is SendScreen) {
                currentFragment.setAddress(paymentURL.address)
                currentFragment.setText(paymentURL.text)
                currentFragment.setAmount(amount)
            } else {
                nav()?.add(SendScreen.newInstance(paymentURL.address, paymentURL.text, amount))
            }
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

    override fun setFragmentResult(requestKey: String, result: Bundle) {
        supportFragmentManager.setFragmentResult(requestKey, result)
    }

    override fun setFragmentResultListener(
        requestKey: String,
        listener: (requestKey: String, bundle: Bundle) -> Unit
    ) {
        supportFragmentManager.setFragmentResultListener(requestKey, this, listener)
    }

    override fun replace(fragment: BaseFragment, addToBackStack: Boolean) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(hostFragmentId, fragment)
        if (addToBackStack) {
            transaction.addToBackStack(null)
        }
        transaction.runOnCommit { initialized = true }
        transaction.commitNow()
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

    override fun openURL(url: String, external: Boolean) {
        if (external) {
            val uri = Uri.parse(url)
            val action = if (url.startsWith("mailto:")) {
                Intent.ACTION_SENDTO
            } else {
                Intent.ACTION_VIEW
            }
            val intent = Intent(action, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            add(WebFragment.newInstance(url))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tonConnect.onDestroy()
    }

    override fun onPreDraw(): Boolean {
        if (!initialized) {
            return false
        }
        contentView.viewTreeObserver.removeOnPreDrawListener(this)
        return true
    }

}