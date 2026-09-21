package com.tonapps.wallet.data.multichain.device

import com.tonapps.chainkit.core.chain.model.account.WalletKeyPair
import com.tonapps.chainkit.core.chain.model.account.WalletKind
import com.tonapps.log.L
import com.tonapps.wallet.SecureProofProvider
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.DeviceAuthProvider
import com.tonapps.wallet.data.multichain.vault.DeviceSession
import com.tonapps.wallet.data.multichain.vault.VaultRepository
import com.tonapps.wallet.data.multichain.vault.WalletKeyRepository
import io.walletapi.models.DevicePlatform
import io.walletapi.models.DeviceTokens
import io.walletapi.models.RefreshDeviceRequest
import io.walletapi.models.RegisterDeviceRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DeviceRegistrationException(cause: Throwable) : Exception(cause)

class SecureDeviceRepository(
    private val vaultRepository: VaultRepository,
    private val walletKeyRepository: WalletKeyRepository,
    private val proofProvider: SecureProofProvider,
    private val api: API,
) : DeviceAuthProvider {

    private val mutex = Mutex()

    private val walletAuthTokens = ConcurrentHashMap<String, String>()

    override fun accessToken(): String? {
        vaultRepository.getDeviceSession()?.let { session ->
            return session.accessToken
        }

        return runBlocking { loadAccessToken() }
    }

    override fun refreshToken(expiredToken: String): String? = runBlocking {
        renewAccessToken(expiredToken)
    }

    // A wallet token stays valid for exactly as long as the access token it signs, so it is cached
    // under both and every entry minted for an earlier access token is dropped on the next miss.
    override fun walletAuthToken(walletId: String, accessToken: String): String? {
        val key = "${walletId}_$accessToken"

        walletAuthTokens[key]
            ?.let { return it }

        val token = signWalletAuthToken(walletId, accessToken)
            ?: return null

        walletAuthTokens.keys.removeAll { !it.endsWith("_$accessToken") }
        walletAuthTokens[key] = token

        return token
    }

    private fun signWalletAuthToken(walletId: String, accessToken: String): String? {
        if (!walletKeyRepository.hasAppKey(walletId)) {
            return null
        }

        val appPrivateKey = runBlocking {
            walletKeyRepository.awaitAppPrivateKey(walletId, WALLET_AUTH_UNLOCK_TIMEOUT_MS)
        } ?: return null

        return try {
            proofProvider.walletAuthToken(
                keyPair = WalletKeyPair.fromPrivateKey(appPrivateKey, WALLET_KIND),
                accessToken = accessToken,
            )
        } catch (e: Throwable) {
            L.e(e)
            null
        } finally {
            appPrivateKey.fill(0)
        }
    }

    suspend fun loadAccessToken(): String? = withContext(Dispatchers.IO) {
        vaultRepository.getDeviceSession()?.accessToken ?: mutex.withLock {
            vaultRepository.getDeviceSession()?.accessToken ?: try {
                register().accessToken
            } catch (e: Throwable) {
                L.e(e)
                null
            }
        }
    }

    suspend fun renewAccessToken(expiredToken: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = vaultRepository.getDeviceSession()
            if (current != null && current.accessToken != expiredToken) {
                return@withLock current.accessToken
            }

            try {
                val refreshed = current?.let { refresh(it) } ?: register()
                refreshed.accessToken
            } catch (e: Throwable) {
                L.e(e)
                null
            }
        }
    }

    suspend fun getDeviceId(): String? = withContext(Dispatchers.IO) {
        vaultRepository.getDeviceId()
    }

    suspend fun requireDeviceId(): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            vaultRepository.getDeviceSession()
                ?.deviceId
                ?: register().deviceId
        }
    }

    suspend fun registerIfNeeded(): String? {
        return try {
            requireDeviceId()
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }

    private suspend fun register(): DeviceSession {
        val keyPair = vaultRepository.getOrCreateDeviceKey()
            ?: throw DeviceRegistrationException(IllegalStateException("No device certificate"))

        try {
            val challenge = api.multichain.auth.getDeviceChallenge().challenge
            val proof = proofProvider.deviceRegisterProof(
                keyPair = keyPair,
                platform = PLATFORM.value,
                appId = PUSH_APP_ID,
                clientVersion = api.appVersionName,
                challenge = challenge,
            )

            return save(
                api.multichain.auth.registerDevice(
                    RegisterDeviceRequest(
                        devicePub = proof.publicKey,
                        deviceProof = proof.signature,
                        challenge = challenge,
                        platform = PLATFORM,
                        appId = PUSH_APP_ID,
                        clientVersion = api.appVersionName,
                    )
                )
            )
        } catch (e: Throwable) {
            L.e(e)
            throw DeviceRegistrationException(e)
        } finally {
            keyPair.privateKey.fill(0)
        }
    }

    private suspend fun refresh(current: DeviceSession): DeviceSession {
        val keyPair = vaultRepository.getOrCreateDeviceKey()
            ?: throw DeviceRegistrationException(IllegalStateException("No device certificate"))

        val tokens = try {
            val proof = proofProvider.deviceRefreshProof(
                keyPair = keyPair,
                deviceId = current.deviceId,
                refreshToken = current.refreshToken,
            )

            api.multichain.auth.refreshDevice(
                RefreshDeviceRequest(
                    deviceId = UUID.fromString(current.deviceId),
                    refreshToken = current.refreshToken,
                    deviceProof = proof.signature,
                )
            )
        } catch (e: Throwable) {
            L.e(e)
            return register()
        } finally {
            keyPair.privateKey.fill(0)
        }

        return save(tokens)
    }

    private fun save(tokens: DeviceTokens): DeviceSession {
        val session = DeviceSession(
            deviceId = tokens.deviceId.toString(),
            accessToken = tokens.deviceJwt,
            refreshToken = tokens.refreshToken,
        )

        vaultRepository.saveDeviceSession(session)

        return session
    }

    private companion object {
        val PLATFORM = DevicePlatform.android
        val WALLET_KIND = WalletKind.Multichain

        const val PUSH_APP_ID = 695609596302L

        const val WALLET_AUTH_UNLOCK_TIMEOUT_MS = 60_000L
    }
}
