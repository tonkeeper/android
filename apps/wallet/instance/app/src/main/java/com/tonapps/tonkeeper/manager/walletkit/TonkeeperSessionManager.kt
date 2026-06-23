package com.tonapps.tonkeeper.manager.walletkit

import com.tonapps.log.L
import androidx.core.net.toUri
import com.tonapps.security.CryptoBox
import com.tonapps.security.hex
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import io.ton.walletkit.api.generated.TONDAppInfo
import io.ton.walletkit.model.TONUserFriendlyAddress
import io.ton.walletkit.session.SessionFilter
import io.ton.walletkit.session.TONConnectSession
import io.ton.walletkit.session.TONConnectSessionManager
import java.time.Instant

internal fun normalizedHost(urlOrDomain: String): String {
    return try {
        java.net.URL(urlOrDomain).host
            ?.lowercase()
            ?: ""
    } catch (_: Exception) {
        urlOrDomain.lowercase()
    }
}

/**
 * Session manager that bridges the SDK to Tonkeeper's database.
 */
class TonkeeperSessionManager(
    private val accountRepository: AccountRepository,
    private val dAppsRepository: DAppsRepository,
) : TONConnectSessionManager {

    override suspend fun createSession(
        sessionId: String,
        dAppInfo: TONDAppInfo,
        walletId: String,
        walletAddress: String,
        isJsBridge: Boolean,
    ): TONConnectSession {
        val wallet = accountRepository.getWallets().find { it.id == walletId }
            ?: throw IllegalArgumentException("Wallet not found: $walletId")

        val keyPair = CryptoBox.keyPair()
        val appUrl = (dAppInfo.url ?: "").removeSuffix("/").toUri()

        val connection = AppConnectEntity(
            accountId = wallet.accountId,
            network = wallet.network,
            clientId = sessionId,
            type = if (isJsBridge) AppConnectEntity.Type.Internal else AppConnectEntity.Type.External,
            appUrl = appUrl,
            keyPair = keyPair,
            proofSignature = null,
            proofPayload = null,
            pushEnabled = false
        )

        if (dAppsRepository.newConnect(connection)) {
            return SessionHelper.buildSession(
                connection = connection,
                walletId = walletId,
                walletAddress = walletAddress,
                dAppInfo = dAppInfo,
            )
        } else {
            throw UnsupportedOperationException("Failed to create connections for session")
        }
    }

    override suspend fun getSession(sessionId: String): TONConnectSession? {
        val connection = dAppsRepository.getConnections().find { it.clientId == sessionId } ?: return null
        val wallet = accountRepository.getWallets().find {
            it.accountId == connection.accountId && it.network == connection.network
        } ?: return null
        val app = dAppsRepository.getApp(connection.appUrl)
        val dApp = TONDAppInfo(app.name, null, app.url.toString(), app.iconUrl.toString(), null)

        return runCatching {
            SessionHelper.buildSession(connection, wallet.id, wallet.address, dApp)
        }.onFailure { L.e(it, "Failed to build session for $sessionId") }.getOrNull()
    }

    override suspend fun getSessions(filter: SessionFilter?): List<TONConnectSession> {
        val wallets = accountRepository.getWallets()
        val allConnections = dAppsRepository.getConnections()
        val filterDomainHost = filter?.domain?.let { normalizedHost(it) }

        return allConnections.mapNotNull { connection ->
            val wallet = wallets.find {
                it.accountId == connection.accountId && it.network == connection.network
            } ?: return@mapNotNull null

            if (filter?.walletId != null && filter.walletId != wallet.id) return@mapNotNull null //

            if (filterDomainHost != null) {
                val connDomain = normalizedHost(connection.appUrl.toString())
                if (connDomain != filterDomainHost) return@mapNotNull null
            }

            if (filter?.isJsBridge != null) {
                val connectionIsJsBridge = connection.type == AppConnectEntity.Type.Internal
                if (connectionIsJsBridge != filter.isJsBridge) return@mapNotNull null
            }

            runCatching {
                SessionHelper.buildSession(connection, wallet.id, wallet.address)
            }.onFailure {
                L.e(it, "Failed to build session for connection ${connection.clientId}")
            }.getOrNull()
        }
    }

    override suspend fun removeSession(sessionId: String) {
        val connection = dAppsRepository.getConnections().find { it.clientId == sessionId } ?: return
        dAppsRepository.deleteConnect(connection)
    }

    override suspend fun removeSessions(filter: SessionFilter?) {
        val sessionIds = getSessions(filter).map { it.sessionId }.toSet()
        dAppsRepository.getConnections()
            .filter { it.clientId in sessionIds }
            .forEach { dAppsRepository.deleteConnect(it) }
    }

    override suspend fun clearSessions() {
        val connections = dAppsRepository.getConnections()
        for (connection in connections) {
            dAppsRepository.deleteConnect(connection)
        }
    }

    private object SessionHelper {
        private const val SCHEMA_VERSION = 1

        fun buildSession(
            connection: AppConnectEntity,
            walletId: String,
            walletAddress: String,
            dAppInfo: TONDAppInfo? = null,
        ): TONConnectSession {
            return TONConnectSession(
                sessionId = connection.clientId,
                walletId = walletId,
                walletAddress = TONUserFriendlyAddress(walletAddress),
                createdAt = Instant.ofEpochMilli(connection.timestamp).toString(),
                lastActivityAt = Instant.now().toString(),
                privateKey = hex(connection.keyPair.privateKey),
                publicKey = hex(connection.keyPair.publicKey),
                domain = normalizedHost(connection.appUrl.toString()),
                dAppName = dAppInfo?.name,
                dAppDescription = dAppInfo?.description,
                dAppUrl = dAppInfo?.url ?: connection.appUrl.toString(),
                dAppIconUrl = dAppInfo?.iconUrl,
                isJsBridge = connection.type == AppConnectEntity.Type.Internal,
                schemaVersion = SCHEMA_VERSION,
            )
        }
    }
}
