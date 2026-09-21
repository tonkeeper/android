package com.tonapps.wallet.data.multichain.device

import com.tonapps.chainkit.core.net.module.SessionTokenProvider

class DeviceSessionTokenProvider(
    private val repository: SecureDeviceRepository,
) : SessionTokenProvider {

    override suspend fun getAccessToken(): String? {
        return repository.loadAccessToken()
    }

    override suspend fun refreshToken(expiredToken: String): String? {
        return repository.renewAccessToken(expiredToken)
    }
}
