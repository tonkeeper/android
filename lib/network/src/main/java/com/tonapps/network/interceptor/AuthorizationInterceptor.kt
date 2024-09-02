package com.tonapps.network.interceptor

import android.net.Uri
import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

class AuthorizationInterceptor(
    private val type: Type = Type.NONE,
    private val token: String,
    private val allowDomains: List<String>
): Interceptor {

    companion object {
        fun bearer(
            token: String,
            allowDomains: List<String>
        ) = AuthorizationInterceptor(Type.BEARER, token, allowDomains)
    }

    enum class Type {
        NONE,
        BASIC,
        BEARER
    }

    private val domains = allowDomains.mapNotNull { Uri.parse(it).host }

    private val headerValue: String by lazy {
        when(type) {
            Type.BEARER -> "Bearer $token"
            Type.BASIC -> "Basic $token"
            else -> token
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url
        val domain = url.host
        if ((domains.isNotEmpty() && !domains.contains(domain))) {
            return chain.proceed(original)
        }

        val request = original.newBuilder()
            .header("Authorization", headerValue)
            .method(original.method, original.body)
            .build()

        return chain.proceed(request)
    }
}