package com.tonapps.extensions

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private var cachedStoreCountry: String? = null
private var hasFetched = false

val Context.deviceCountry: String
    get() = Locale.getDefault().country.uppercase()

suspend fun Context.getStoreCountry(): String? {
    if (hasFetched) return cachedStoreCountry

    return getPlayCountry().also {
        cachedStoreCountry = it
        hasFetched = true
    }
}

private suspend fun Context.getPlayCountry(): String? {
    return suspendCancellableCoroutine { continuation ->
        val referrerClient = InstallReferrerClient.newBuilder(this).build()

        referrerClient.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                when (responseCode) {
                    InstallReferrerClient.InstallReferrerResponse.OK -> {
                        try {
                            val referrerUrl = referrerClient.installReferrer.installReferrer
                            val country = referrerUrl
                                .substringAfter("utm_country=", "")
                                .substringBefore("&")
                                .takeIf { it.isNotEmpty() }

                            continuation.resume(country?.uppercase())
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        } finally {
                            referrerClient.endConnection()
                        }
                    }

                    else -> {
                        continuation.resume(null)
                        referrerClient.endConnection()
                    }
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                continuation.resume(null)
            }
        })

        continuation.invokeOnCancellation { referrerClient.endConnection() }
    }
}
