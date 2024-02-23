package com.tonapps.tonkeeper.fragment.root

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.tonapps.emoji.Emoji
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.core.Coin
import com.tonapps.tonkeeper.core.PaymentURL
import com.tonapps.tonkeeper.core.deeplink.DeepLink
import com.tonapps.tonkeeper.fragment.intro.IntroFragment
import uikit.base.BaseFragment
import com.tonapps.tonkeeper.fragment.main.MainFragment
import com.tonapps.tonkeeper.fragment.web.WebFragment
import com.tonapps.tonkeeper.core.tonconnect.TonConnect
import com.tonapps.tonkeeper.dialog.TransactionDialog
import com.tonapps.tonkeeper.dialog.fiat.FiatDialog
import com.tonapps.tonkeeper.fragment.send.SendScreen
import com.tonapps.tonkeeper.ui.screen.init.InitAction
import com.tonapps.tonkeeper.ui.screen.init.InitFragment
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.R
import uikit.extensions.collectFlow
import uikit.extensions.findFragment
import uikit.navigation.NavigationActivity
import java.util.Locale

class RootActivity: NavigationActivity() {

    private val rootViewModel: RootViewModel by viewModel()

    val fiatDialog: FiatDialog by lazy {
        FiatDialog(this, lifecycleScope)
    }

    val transactionDialog: TransactionDialog by lazy {
        TransactionDialog(this, lifecycleScope)
    }

    lateinit var tonConnect: TonConnect

    private lateinit var uiHandler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(App.instance.getThemeRes())
        super.onCreate(savedInstanceState)
        Emoji.init(this)

        uiHandler = Handler(mainLooper)
        tonConnect = TonConnect(this)

        collectFlow(rootViewModel.hasWalletFlow) { init(it, false) }
        collectFlow(rootViewModel.changeWallet) { init(hasWallet = true, recreate = true) }
        collectFlow(rootViewModel.addFragmentAction) { add(it) }

        handleIntent(intent)
    }

    fun init(hasWallet: Boolean, recreate: Boolean) {
        if (hasWallet) {
            setMainFragment(recreate)
        } else {
            setIntroFragment()
        }
    }

    private fun setIntroFragment() {
        if (setPrimaryFragment(IntroFragment.newInstance())) {
            // hideLockPassword()
        }
    }

    private fun setMainFragment(recreate: Boolean) {
        if (setPrimaryFragment(MainFragment.newInstance(), recreate)) { //  && !Password.isUnlocked()
            //showLockPassword()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /*
    /*if (DeepLink.isSupportedUri(uri)) {
                        deepLink.handle(uri)
                    } else {
                        add(WebFragment.newInstance(url))
                    }*/
     */
    private fun handleIntent(intent: Intent) {
        val uri = intent.data ?: return
        rootViewModel.processDeepLink(uri)
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
        } else {
            rootViewModel.processDeepLink(uri)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tonConnect.onDestroy()
    }
}