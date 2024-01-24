package com.tonapps.singer.screen.root

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.tonapps.singer.screen.intro.IntroFragment
import com.tonapps.singer.screen.key.KeyFragment
import com.tonapps.singer.screen.main.MainFragment
import com.tonapps.singer.screen.root.action.RootAction
import com.tonapps.singer.screen.sign.SignFragment
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.navigation.NavigationActivity

class RootActivity: NavigationActivity() {

    private val rootViewModel: RootViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rootViewModel.hasKeys.onEach(::init).launchIn(lifecycleScope)
        rootViewModel.action.flowWithLifecycle(lifecycle).onEach(::onAction).launchIn(lifecycleScope)
        handleIntent(intent)
    }

    private fun onAction(action: RootAction) {
        when (action) {
            is RootAction.RequestBodySign -> add(SignFragment.newInstance(action.id, action.body, action.qr))
            is RootAction.ResponseBoc -> responseBoc(action.boc)
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