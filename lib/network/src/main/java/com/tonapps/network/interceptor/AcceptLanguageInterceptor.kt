package com.tonapps.network.interceptor

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.util.Locale

class AcceptLanguageInterceptor(
    private val locale: Locale
): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        val request = original.newBuilder()
            .header("Accept-Language", locale.toString())
            .method(original.method, original.body)
            .build()

        return chain.proceed(request)
    }

}