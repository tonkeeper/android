package com.tonapps.wallet.api

interface DeviceAuthProvider {

    fun accessToken(): String?

    fun refreshToken(expiredToken: String): String?

    fun walletAuthToken(walletId: String, accessToken: String): String?
}
