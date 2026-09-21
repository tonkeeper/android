package com.tonapps.wallet

import com.tonapps.chainkit.core.chain.model.account.KeyPair
import com.tonapps.chainkit.core.chain.model.account.WalletAuth
import com.tonapps.chainkit.core.chain.model.account.WalletKeyPair

class SecureProofProvider {

    private val auth = WalletAuth()

    fun generateDeviceCertificate(): KeyPair {
        return auth.generateDeviceCertificate()
    }

    fun deviceRegisterProof(
        keyPair: KeyPair,
        platform: String,
        appId: Long,
        clientVersion: String,
        challenge: String,
    ): WalletAuth.Proof {
        return auth.signDeviceRegisterProof(
            keyPair = keyPair,
            platform = platform,
            appId = appId,
            clientVersion = clientVersion,
            challenge = challenge,
        )
    }

    fun walletAuthToken(
        keyPair: WalletKeyPair,
        accessToken: String,
    ): String {
        return auth.walletAuthToken(keyPair, accessToken)
    }

    fun deviceRefreshProof(
        keyPair: KeyPair,
        deviceId: String,
        refreshToken: String,
    ): WalletAuth.Proof {
        return auth.signDeviceRefreshProof(
            keyPair = keyPair,
            deviceId = deviceId,
            refreshToken = refreshToken,
        )
    }

    fun batteryProof(
        keyPair: WalletKeyPair,
        walletId: String,
        chain: String,
        boc: String,
    ): WalletAuth.Proof {
        return auth.signBatterySendProof(
            keyPair = keyPair,
            walletId = walletId,
            chain = chain,
            boc = boc,
        )
    }
}
