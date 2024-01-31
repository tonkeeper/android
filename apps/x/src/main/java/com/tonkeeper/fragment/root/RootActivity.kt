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
import com.tonkeeper.fragment.passcode.lock.LockScreen
import com.tonkeeper.fragment.send.SendScreen
import com.tonkeeper.fragment.settings.accounts.AccountsScreen
import com.tonkeeper.fragment.wallet.init.InitAction
import com.tonkeeper.fragment.wallet.init.InitScreen
import kotlinx.coroutines.launch
import uikit.extensions.doOnEnd
import uikit.extensions.findFragment
import uikit.extensions.hapticConfirm
import uikit.extensions.startAnimation
import uikit.navigation.NavigationActivity

class RootActivity: NavigationActivity(), DeepLink.Processor {

    private val deepLink = DeepLink(this)

    val fiatDialog: FiatDialog by lazy {
        FiatDialog(this, lifecycleScope)
    }

    val transactionDialog: TransactionDialog by lazy {
        TransactionDialog(this, lifecycleScope)
    }

    lateinit var tonConnect: TonConnect

    private lateinit var uiHandler: Handler
    private var initialized = false
    private var lastIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiHandler = Handler(mainLooper)
        tonConnect = TonConnect(this)

        initRoot(false, intent)
    }

    override fun openUri(uri: Uri): Boolean {
        val fragment = resolveScreen(uri) ?: return false
        add(fragment)
        return true
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
        } else if (uri.toString() == AccountsScreen.DeepLink && !hasFragment<AccountsScreen>()) {
            return AccountsScreen.newInstance()
        } else if (DeepLink.isTonkeeperUri(uri)) {
            return resolveTonkeeperScreen(uri)
        }
        return null
    }

    private fun resolveTonkeeperScreen(uri: Uri): BaseFragment? {
        val firstPath = DeepLink.getTonkeeperUriFirstPath(uri)
        if (firstPath == "signer") {
            return resolveSinger(uri)
        }
        return null
    }

    private fun resolveSinger(uri: Uri): BaseFragment? {
        val pkBase64 = uri.getQueryParameter("pk") ?: return null
        val name = uri.getQueryParameter("name")
        return InitScreen.newInstance(InitAction.Signer, name, pkBase64)
    }

    private inline fun <reified F> hasFragment(): Boolean {
        return findFragment<F>() != null
    }

    private inline fun <reified F> findFragment(): F? {
        return supportFragmentManager.findFragment<F>()
    }

    private fun resolveSendScreen(uri: Uri): BaseFragment? {
        val paymentURL = PaymentURL(uri)
        if (paymentURL.action != PaymentURL.ACTION_TRANSFER) {
            return null
        }
        val amount = Coin.toCoins(paymentURL.amount)
        val currentSendScreen = findFragment<SendScreen>()
        if (currentSendScreen != null) {
            currentSendScreen.forceSetAddress(paymentURL.address)
            currentSendScreen.forceSetAmount(amount)
            currentSendScreen.forceSetComment(paymentURL.text)
            currentSendScreen.forceSetJetton(paymentURL.jettonAddress)
            return null
        }
        return SendScreen.newInstance(paymentURL.address, paymentURL.text, amount, paymentURL.jettonAddress)
    }

    override fun onResume() {
        super.onResume()
        if (!initialized) {
            return
        }

        val intentScreen = resolveIntent(lastIntent)
        lastIntent = null

        if (intentScreen is InitScreen) {
            add(intentScreen)
        }

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

    private fun setIntroFragment(intent: Intent?) {
        val introFragment = IntroFragment.newInstance()
        val intentScreen = resolveIntent(intent)
        if (intentScreen is InitScreen) {
            lastIntent = null
        }

        supportFragmentManager.commit {
            replace(hostFragmentId, introFragment, introFragment.javaClass.name)
            setPrimaryNavigationFragment(introFragment)
            if (intentScreen is InitScreen) {
                add(hostFragmentId, intentScreen)
            }
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
        if (true) {
            return
        }
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

    override fun initRoot(skipPasscode: Boolean, intent: Intent?) {
        lifecycleScope.launch {
            val wallet = App.walletManager.getWalletInfo()
            if (wallet == null) {
                setIntroFragment(intent)
            } else {
                setMainFragment(skipPasscode, intent)
            }
        }
    }

    override fun openURL(url: String, external: Boolean) {
        if (url.isBlank()) {
            return
        }

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
        } else if (DeepLink.isSupportedUri(uri)) {
            deepLink.handle(uri)
        } else {
            add(WebFragment.newInstance(url))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tonConnect.onDestroy()
    }
}