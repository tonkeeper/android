package com.tonapps.tonkeeper.helper

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.tonapps.tonkeeper.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class ReferrerClientHelper(
    private val context: Context,
    private val environment: Environment,
) {

    suspend fun getInstallReferrer(): String? {
        if (!environment.isFromGooglePlay) {
            return null
        }
        return withContext(Dispatchers.Main) {
            val client = try {
                InstallReferrerClient.newBuilder(context).build()
            } catch (ignored: Throwable) {
                return@withContext null
            }
            try {
                client.getReferrerDetails()?.installReferrer
            } finally {
                client.endConnection()
            }
        }
    }

    private companion object {

        suspend fun InstallReferrerClient.getReferrerDetails(): ReferrerDetails? = suspendCancellableCoroutine { continuation ->
            fun complete(details: ReferrerDetails?) {
                if (continuation.isActive) {
                    continuation.resume(details)
                }
            }

            try {
                startConnection(object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK && isReady) {
                            complete(runCatching { installReferrer }.getOrNull())
                        } else {
                            complete(null)
                        }
                    }

                    override fun onInstallReferrerServiceDisconnected() {
                        complete(null)
                    }
                })
            } catch (e: Throwable) {
                complete(null)
            }
        }
    }

}
