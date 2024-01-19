package com.tonapps.singer.screen.root

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import com.tonapps.singer.R
import com.tonapps.singer.core.TonkeeperApp
import com.tonapps.singer.screen.intro.IntroFragment
import com.tonapps.singer.screen.key.KeyFragment
import com.tonapps.singer.screen.main.MainFragment
import com.tonapps.singer.screen.root.action.RootAction
import com.tonapps.singer.screen.sign.SignFragment
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseActivity
import uikit.base.BaseFragment
import uikit.extensions.doOnEnd
import uikit.extensions.hapticConfirm
import uikit.extensions.startAnimation
import uikit.navigation.Navigation
import uikit.navigation.Navigation.Companion.navigation
import uikit.navigation.NavigationActivity

class RootActivity: NavigationActivity() {

    private val rootViewModel: RootViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootViewModel.hasKeys.onEach(::init).launchIn(lifecycleScope)
        rootViewModel.action.onEach(::onAction).launchIn(lifecycleScope)
        handleIntent(intent)
    }

    private fun onAction(action: RootAction) {
        if (action is RootAction.KeyDetails) {
            add(KeyFragment.newInstance(action.id))
        } else if (action is RootAction.RequestBodySign) {
            add(SignFragment.newInstance(action.id, action.body, action.qr))
        } else if (action is RootAction.ResponseBoc) {
            responseBoc(action.boc)
        }
    }

    private fun responseBoc(boc: String) {
        val intent = Intent()
        intent.putExtra("boc", boc)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun init(hasKeys: Boolean) {
        if (hasKeys) {
            setMainFragment()
        } else {
            setIntroFragment()
        }
    }

    private fun handleUri(uri: Uri, qr: Boolean) {
        rootViewModel.processUri(uri, qr)
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data ?: return

        handleUri(uri, intent.action != Intent.ACTION_SEND)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun isInitialized() = rootViewModel.initialized

    private fun setIntroFragment() {
        val introFragment = IntroFragment.newInstance()

        setPrimaryFragment(introFragment)
    }

    private fun setMainFragment() {
        val mainFragment = MainFragment.newInstance()

        setPrimaryFragment(mainFragment)
    }

    override fun initRoot(skipPasscode: Boolean, intent: Intent?) {

    }

    override fun openURL(url: String, external: Boolean) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}