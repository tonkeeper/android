package com.tonapps.wallet.api.internal

import com.tonapps.wallet.api.DeviceAuthProvider
import io.infrastructure.ApiTags
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

internal class DeviceAuthInterceptor(
    private val hosts: () -> Collection<String>,
    private val provider: () -> DeviceAuthProvider?,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isAuthorizable(request)) {
            return chain.proceed(request)
        }

        val auth = provider() ?: return chain.proceed(request)
        val token = auth.accessToken() ?: return chain.proceed(request)

        val response = chain.proceed(request.authorized(auth, token))
        if (response.code != HTTP_UNAUTHORIZED) {
            return response
        }

        val refreshedToken = auth.refreshToken(token) ?: return response
        response.close()

        return chain.proceed(request.authorized(auth, refreshedToken))
    }

    private fun isAuthorizable(request: Request): Boolean {
        if (request.tag(ApiTags.DeviceAuth::class.java) == null) {
            return false
        }

        val hosts = hosts()
        return hosts.isEmpty() || request.url.host in hosts
    }

    private fun Request.authorized(auth: DeviceAuthProvider, token: String): Request {
        return newBuilder()
            .header("Authorization", BEARER + token)
            .build()
            .withWalletAuth(auth, token)
    }

    private fun Request.withWalletAuth(auth: DeviceAuthProvider, token: String): Request {
        val walletId = walletId() ?: return this
        val walletToken = auth.walletAuthToken(walletId, token) ?: return this

        val builder = newBuilder().header(WALLET_AUTHORIZATION, walletToken)
        if (header(WALLET_ID) == null) {
            builder.header(WALLET_ID, walletId)
        }

        return builder.build()
    }

    private fun Request.walletId(): String? {
        return tag(ApiTags.WalletAuth::class.java)?.walletId
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401

        const val BEARER = "Bearer "

        const val WALLET_AUTHORIZATION = "X-Wallet-Authorization"

        const val WALLET_ID = "X-Wallet-ID"
    }
}
