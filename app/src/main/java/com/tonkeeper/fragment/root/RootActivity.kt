package com.tonkeeper.fragment.root

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.addCallback
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
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
import kotlinx.coroutines.launch
import uikit.extensions.doOnEnd
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

    private lateinit var contentView: View
    private lateinit var toastView: AppCompatTextView
    private var initialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        contentView = findViewById(android.R.id.content)
        contentView.viewTreeObserver.addOnPreDrawListener(this)

        toastView = findViewById(R.id.toast)

        handleIntent(intent)
        tonConnect = TonConnect(this)

        initRoot(false)

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

    private fun handleIntent(intent: Intent) {
        val data = intent.data ?: return
        if (!deepLink.handle(data)) {
            handleUri(data)
        }
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
            sendCoin(paymentURL.address, paymentURL.text, amount)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
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

    private fun setMainFragment(skipPasscode: Boolean) {
        val mainFragment = MainFragment.newInstance()

        supportFragmentManager.commit {
            replace(hostFragmentId, mainFragment, mainFragment.javaClass.name)
            setPrimaryNavigationFragment(mainFragment)
            if (App.settings.lockScreen && App.passcode.hasPinCode && !skipPasscode) {
                add(hostFragmentId, LockScreen.newInstance())
            }
            runOnCommit {
                clearBackStack()
                tonConnect.onCreate()
                initialized = true
            }
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

    override fun initRoot(skipPasscode: Boolean) {
        lifecycleScope.launch {
            val wallet = App.walletManager.getWalletInfo()
            if (wallet == null) {
                setIntroFragment()
            } else {
                setMainFragment(skipPasscode)
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