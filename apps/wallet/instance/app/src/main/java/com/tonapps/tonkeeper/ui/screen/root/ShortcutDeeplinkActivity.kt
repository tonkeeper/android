package com.tonapps.tonkeeper.ui.screen.root

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.tonapps.extensions.getStringValue
import com.tonapps.extensions.toUriOrNull
import com.tonapps.tonkeeper.manager.shortcut.AppShortcutRepository
import org.koin.android.ext.android.inject
import uikit.base.BaseHiddenActivity

/**
 * Not-exported entry point for dApp home-screen shortcuts. Shortcuts target this activity instead of
 * the exported [RootActivity], so a third-party app cannot force Tonkeeper to open an arbitrary URL
 * in the in-app WebView by launching [RootActivity] with a forged "dapp_deeplink" extra. The URL is
 * handed to [RootActivity] through an in-memory bus, never as a forgeable intent extra.
 */
class ShortcutDeeplinkActivity : BaseHiddenActivity() {

    private val appShortcutRepository: AppShortcutRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.extras?.getStringValue(EXTRA_DAPP_DEEPLINK)?.toUriOrNull()?.let {
            appShortcutRepository.submitDeeplink(it)
        }
        startActivity(Intent(this, RootActivity::class.java))
        finish()
    }

    companion object {
        const val EXTRA_DAPP_DEEPLINK = "dapp_deeplink"

        fun intent(context: Context, url: String): Intent =
            Intent(context, ShortcutDeeplinkActivity::class.java).apply {
                putExtra(EXTRA_DAPP_DEEPLINK, url)
                action = Intent.ACTION_MAIN
            }
    }
}
