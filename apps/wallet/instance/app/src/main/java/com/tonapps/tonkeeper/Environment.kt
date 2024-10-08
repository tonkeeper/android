package com.tonapps.tonkeeper

import android.content.Context
import android.os.Build
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.tonapps.tonkeeper.os.AppInstall

class Environment(context: Context) {

    private val installerSource: AppInstall.Source by lazy { AppInstall.request(context) }

    val isGooglePlayServicesAvailable: Boolean by lazy {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        resultCode == ConnectionResult.SUCCESS
    }

    val isGooglePlayAvailable: Boolean by lazy {
        // installerSource == AppInstall.Source.GOOGLE_PLAY && isGooglePlayServicesAvailable
        true
    }
}