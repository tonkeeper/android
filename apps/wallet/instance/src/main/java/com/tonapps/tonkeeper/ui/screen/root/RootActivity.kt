package com.tonapps.tonkeeper.ui.screen.root

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.ui.screen.main.MainScreen
import com.tonapps.tonkeeper.core.tonconnect.TonConnect
import com.tonapps.tonkeeper.dialog.TransactionDialog
import com.tonapps.tonkeeper.dialog.fiat.FiatDialog
import com.tonapps.tonkeeper.fragment.tonconnect.auth.TCAuthFragment
import com.tonapps.tonkeeper.fragment.web.WebFragment
import com.tonapps.tonkeeper.ui.component.PasscodeView
import com.tonapps.tonkeeper.ui.screen.init.InitArgs
import com.tonapps.tonkeeper.ui.screen.init.InitScreen
import com.tonapps.tonkeeper.ui.screen.start.StartScreen
import com.tonapps.tonkeeperx.R
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.extensions.collectFlow
import uikit.navigation.NavigationActivity

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

    private lateinit var lockView: View
    private lateinit var lockPasscodeView: PasscodeView

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(rootViewModel.themeId)
        super.onCreate(savedInstanceState)

        uiHandler = Handler(mainLooper)
        tonConnect = TonConnect(this)
        tonConnect.onCreate()

        handleIntent(intent)

        lockView = findViewById(R.id.lock)
        lockPasscodeView = findViewById(R.id.lock_passcode)
        lockPasscodeView.doOnCheck = ::checkPasscode

        ViewCompat.setOnApplyWindowInsetsListener(lockView) { _, insets ->
            val statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            lockView.updatePadding(top = statusInsets.top, bottom = navInsets.bottom)
            insets
        }

        collectFlow(rootViewModel.hasWalletFlow) { init(it) }
        collectFlow(rootViewModel.eventFlow) { event(it) }
        collectFlow(rootViewModel.lockFlow) {
            lockView.visibility = if (it) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        collectFlow(rootViewModel.themeFlow) {
            recreate()
        }
    }

    private fun checkPasscode(code: String) {
        rootViewModel.checkPasscode(code).catch {
            lockPasscodeView.setError()
        }.onEach {
            lockPasscodeView.setSuccess()
        }.launchIn(lifecycleScope)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(R.layout.activity_root)
    }

    fun event(event: RootEvent) {
        if (event is RootEvent.Toast) {
            toast(getString(event.resId))
        } else if (event is RootEvent.Singer) {
            add(InitScreen.newInstance(InitArgs.Type.Signer, event.publicKey, event.name, event.walletSource))
        } else if (event is RootEvent.TonConnect) {
            add(TCAuthFragment.newInstance(event.request))
        } else if (event is RootEvent.Browser) {
            add(WebFragment.newInstance(event.uri))
        }
    }

    fun init(hasWallet: Boolean) {
        if (hasWallet) {
            setMainFragment()
        } else {
            setIntroFragment()
        }
    }

    private fun setIntroFragment() {
        setPrimaryFragment(StartScreen.newInstance())
    }

    private fun setMainFragment() {
        setPrimaryFragment(MainScreen.newInstance())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data ?: return
        rootViewModel.processDeepLink(uri, false)
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
            rootViewModel.processDeepLink(uri, false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tonConnect.onDestroy()
    }
}