package com.tonapps.tonkeeper

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.tonapps.tonkeeper.extensions.isDarkMode
import com.tonapps.tonkeeper.os.AppInstall
import com.tonapps.wallet.data.settings.SettingsRepository
import uikit.compose.AppTheme

class Environment(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    val installerSource: AppInstall.Source by lazy { AppInstall.request(context) }

    val isFromGooglePlay: Boolean by lazy {
        installerSource == AppInstall.Source.GOOGLE_PLAY
    }

    val isGooglePlayServicesAvailable: Boolean by lazy {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        resultCode == ConnectionResult.SUCCESS
    }
}