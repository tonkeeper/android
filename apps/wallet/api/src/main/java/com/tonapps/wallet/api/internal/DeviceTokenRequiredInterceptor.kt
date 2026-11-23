package com.tonapps.wallet.api.internal

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

internal object DeviceTokenRequiredInterceptor : Interceptor {

    private const val AUTHORIZATION = "Authorization"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(AUTHORIZATION) == null) {
            throw IOException("No device token for ${request.url.encodedPath}")
        }

        return chain.proceed(request)
    }
}
