package com.tonapps.network.interceptor

import android.net.Uri
import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

class AuthorizationInterceptor(
    private val type: Type = Type.NONE,
    private val token: String,
    private val allowDomains: List<String>,
    private val ignorePaths: List<String> = emptyList()
): Interceptor {

    companion object {
        fun bearer(
            token: String,
            allowDomains: List<String>,
            ignorePaths: List<String> = emptyList()
        ) = AuthorizationInterceptor(Type.BEARER, token, allowDomains, ignorePaths)
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

    private fun isIgnorePath(path: String): Boolean {
        return ignorePaths.contains(path)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url
        val domain = url.host
        if ((domains.isNotEmpty() && !domains.contains(domain)) || isIgnorePath(url.encodedPath)) {
            return chain.proceed(original)
        }

        val request = original.newBuilder()
            .header("Authorization", headerValue)
            .method(original.method, original.body)
            .build()

        return chain.proceed(request)
    }
}