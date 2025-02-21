package com.tonapps.tonkeeper.helper

import android.content.Context
import android.os.CancellationSignal
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.tonapps.tonkeeper.Environment
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ReferrerClientHelper(context: Context, environment: Environment) {

    private val referrerClient: InstallReferrerClient? by lazy {
        if (environment.isFromGooglePlay) {
            try {
                InstallReferrerClient.newBuilder(context).build()
            } catch (ignored: Throwable) {
                null
            }
        } else {
            null
        }
    }

    suspend fun getInstallReferrer(): String? {
        return referrerClient?.getReferrerDetails()?.installReferrer
    }

    private companion object {

        suspend fun InstallReferrerClient.getReferrerDetails(): ReferrerDetails? = suspendCancellableCoroutine { continuation ->
            try {
                continuation.invokeOnCancellation { endConnection() }
                startConnection(object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        if (continuation.isActive) {
                            if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK && isReady) {
                                continuation.resume(installReferrer)
                            } else {
                                continuation.resume(null)
                            }
                        }
                        endConnection()
                    }
                    override fun onInstallReferrerServiceDisconnected() { }
                })
            } catch (e: Throwable) {
                continuation.resume(null)
            }
        }
    }

}