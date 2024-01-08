package com.tonkeeper.fragment.root

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.addCallback
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.ReportFragment
import androidx.lifecycle.ReportFragment.Companion.reportFragment
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeperx.R
import com.tonkeeper.App
import com.tonkeeper.core.Coin
import com.tonkeeper.core.PaymentURL
import com.tonkeeper.core.deeplink.DeepLink
import com.tonkeeper.fragment.intro.IntroFragment
import uikit.base.BaseActivity
import uikit.base.BaseFragment
import uikit.navigation.Navigation
import com.tonkeeper.fragment.main.MainFragment
import com.tonkeeper.fragment.web.WebFragment
import com.tonkeeper.core.tonconnect.TonConnect
import com.tonkeeper.dialog.TransactionDialog
import com.tonkeeper.dialog.fiat.FiatDialog
import com.tonkeeper.extensions.sendCoin
import com.tonkeeper.fragment.passcode.lock.LockScreen
import com.tonkeeper.fragment.send.SendScreen
import kotlinx.coroutines.launch
import uikit.extensions.doOnEnd
import uikit.extensions.findFragment
import uikit.extensions.hapticConfirm
import uikit.extensions.startAnimation

class RootActivity: BaseActivity(), Navigation, ViewTreeObserver.OnPreDrawListener {

    companion object {
        val hostFragmentId = R.id.nav_host_fragment
    }

    private val deepLink = DeepLink(this)

    val fiatDialog: FiatDialog by lazy {
        FiatDialog(this, lifecycleScope)
    }

    val transactionDialog: TransactionDialog by lazy {
        TransactionDialog(this, lifecycleScope)
    }

    lateinit var tonConnect: TonConnect

    private lateinit var uiHandler: Handler
    private lateinit var contentView: View
    private lateinit var toastView: AppCompatTextView
    private var initialized = false
    private var lastIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        uiHandler = Handler(mainLooper)
        contentView = findViewById(android.R.id.content)
        contentView.viewTreeObserver.addOnPreDrawListener(this)

        toastView = findViewById(R.id.toast)

        tonConnect = TonConnect(this)

        initRoot(false, intent)

        onBackPressedDispatcher.addCallback(this) {
            onBackPress()
        }
    }

    private fun onBackPress() {
        val fragment = supportFragmentManager.fragments.lastOrNull() as? BaseFragment ?: return
        if (fragment.onBackPressed()) {
            remove(fragment)
        }
    }

    fun openUri(uri: Uri) {
        val fragment = resolveScreen(uri) ?: return
        add(fragment)
    }

    private fun resolveIntent(intent: Intent?): BaseFragment? {
        val data = intent?.data ?: return null
        if (!deepLink.handle(data)) {
            return resolveScreen(data)
        }
        return null
    }

    private fun resolveScreen(uri: Uri): BaseFragment? {
        if (tonConnect.isSupportUri(uri)) {
            return tonConnect.resolveScreen(uri)
        } else if (uri.scheme == "ton" || uri.host == "app.tonkeeper.com") {
            return resolveSendScreen(uri)
        }
        return null
    }

    private fun resolveSendScreen(uri: Uri): BaseFragment? {
        val paymentURL = PaymentURL(uri)
        if (paymentURL.action == PaymentURL.ACTION_TRANSFER) {
            val amount = Coin.toCoins(paymentURL.amount)
            return SendScreen.newInstance(paymentURL.address, paymentURL.text, amount, null)
        }
        return null
    }

    override fun onResume() {
        super.onResume()
        if (!initialized) {
            return
        }

        val intentScreen = resolveIntent(lastIntent)
        lastIntent = null

        lifecycleScope.launch {
            App.walletManager.getWalletInfo() ?: return@launch

            supportFragmentManager.commit {
                intentScreen?.let {
                    add(hostFragmentId, it)
                }
                insertLockScreenIfNeed(this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lastIntent = intent
    }

    private fun setIntroFragment() {
        val introFragment = IntroFragment.newInstance()

        supportFragmentManager.commit {
            replace(hostFragmentId, introFragment, introFragment.javaClass.name)
            setPrimaryNavigationFragment(introFragment)
            runOnCommit {
                clearBackStack()
                initialized = true
            }
        }
    }

    private fun setMainFragment(skipPasscode: Boolean, intent: Intent?) {
        val mainFragment = MainFragment.newInstance()
        val intentScreen = resolveIntent(intent)
        lastIntent = null
        supportFragmentManager.commit {
            replace(hostFragmentId, mainFragment, mainFragment.javaClass.name)
            setPrimaryNavigationFragment(mainFragment)
            intentScreen?.let {
                add(hostFragmentId, it)
            }
            if (!skipPasscode) {
                insertLockScreenIfNeed(this)
            }
            runOnCommit {
                clearBackStack()
                tonConnect.onCreate()
                initialized = true
            }
        }
    }

    private fun insertLockScreenIfNeed(transaction: FragmentTransaction) {
        if (!App.settings.lockScreen || !App.passcode.hasPinCode) {
            return
        }
        val currentLockScreen = supportFragmentManager.findFragment<LockScreen>()
        if (currentLockScreen == null) {
            transaction.add(hostFragmentId, LockScreen.newInstance())
        }
    }

    private fun clearBackStack() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
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

    override fun initRoot(skipPasscode: Boolean, intent: Intent?) {
        lifecycleScope.launch {
            val wallet = App.walletManager.getWalletInfo()
            if (wallet == null) {
                setIntroFragment()
            } else {
                setMainFragment(skipPasscode, intent)
            }
        }
    }

    override fun add(fragment: BaseFragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.add(hostFragmentId, fragment)
        transaction.commitAllowingStateLoss()
    }

    override fun remove(fragment: Fragment) {
        if (supportFragmentManager.primaryNavigationFragment == fragment) {
            finish()
        } else {
            supportFragmentManager.commitNow {
                remove(fragment)
            }
        }
    }

    override fun openURL(url: String, external: Boolean) {
        val uri = Uri.parse(url)

        if (external) {
            val action = if (url.startsWith("mailto:")) {
                Intent.ACTION_SENDTO
            } else {
                Intent.ACTION_VIEW
            }
            val intent = Intent(action, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else if (deepLink.isSupportedUri(uri)) {
            deepLink.handle(uri)
        } else {
            add(WebFragment.newInstance(url))
        }
    }

    override fun toast(message: String) {
        contentView.hapticConfirm()
        toastView.text = message

        if (toastView.visibility == View.VISIBLE) {
            return
        }

        toastView.visibility = View.VISIBLE
        toastView.startAnimation(uikit.R.anim.toast).doOnEnd {
            toastView.visibility = View.GONE
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