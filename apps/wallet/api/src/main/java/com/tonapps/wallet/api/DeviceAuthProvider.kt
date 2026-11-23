package com.tonapps.wallet.api

interface DeviceAuthProvider {

    fun accessToken(): String?

    fun refreshToken(expiredToken: String): String?

    fun accessTokenExpiresAt(accessToken: String): Long?

    fun walletAuthToken(walletId: String, accessToken: String): String?
}
