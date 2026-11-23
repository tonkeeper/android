package com.tonapps.tonkeeper

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import com.ton_keeper.R
import uikit.extensions.setMoonOnClickListener

class ReinstallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(uikit.R.style.Theme_App_Blue)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContentView(R.layout.activity_reinstall)
        findViewById<Button>(R.id.open_google_play).setMoonOnClickListener { openGooglePlay() }
    }

    private fun openGooglePlay() {
        val marketIntent = Intent(Intent.ACTION_VIEW, "$MARKET_DETAILS_URL$packageName".toUri())
            .setPackage(PLAY_STORE_PACKAGE)
        if (startActivitySafe(marketIntent)) {
            return
        }
        startActivitySafe(Intent(Intent.ACTION_VIEW, "$PLAY_STORE_DETAILS_URL$packageName".toUri()))
    }

    private fun startActivitySafe(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private companion object {
        const val PLAY_STORE_PACKAGE = "com.android.vending"
        const val MARKET_DETAILS_URL = "market://details?id="
        const val PLAY_STORE_DETAILS_URL = "https://play.google.com/store/apps/details?id="
    }
}
