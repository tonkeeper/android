package com.tonapps.tonkeeper.manager.tonconnect

import androidx.annotation.StringRes
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.extensions.equalsRawAddress
import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.core.flags.WalletFeature
import com.tonapps.tonkeeper.extensions.isTonConnectSignMessageAvailable
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.block.AddrStd

/** Why a TON Connect `signMessage` request is refused before the confirmation sheet opens. */
sealed interface TonConnectSignMessageError {

    val bridgeError: BridgeError

    @get:StringRes
    val toastRes: Int

    data object NotSupported : TonConnectSignMessageError {
        override val bridgeError = BridgeError.methodNotSupported("Method not supported")
        override val toastRes = Localization.sign_message_error_not_supported
    }

    data object InvalidMessages : TonConnectSignMessageError {
        override val bridgeError = BridgeError.badRequest("Invalid messages")
        override val toastRes = Localization.sign_message_error_invalid_request
    }

    data object Expired : TonConnectSignMessageError {
        override val bridgeError = BridgeError.badRequest("Request expired")
        override val toastRes = Localization.sign_message_error_expired
    }

    data object NetworkMismatch : TonConnectSignMessageError {
        override val bridgeError = BridgeError.badRequest("Wrong network")
        override val toastRes = Localization.sign_message_error_network_mismatch
    }

    data object AddressMismatch : TonConnectSignMessageError {
        override val bridgeError = BridgeError.badRequest("Wrong address")
        override val toastRes = Localization.sign_message_error_address_mismatch
    }
}

/**
 * Checks a `signMessage` request before the sheet opens, and caps the lifetime of the signed
 * request as TON WalletKit does. Pure Kotlin so it runs in plain JVM unit tests.
 */
object TonConnectSignMessageValidation {

    /** Longest lifetime a dApp may ask for; a request without `valid_until` lives [DEFAULT_TTL]. */
    const val MAX_TTL = 600L
    const val DEFAULT_TTL = 300L

    data class Input(
        val isFeatureEnabled: Boolean,
        val isWalletSupported: Boolean,
        val walletMaxMessages: Int,
        val walletAccountId: String,
        val walletNetworkValue: Int,
        val messageAddresses: List<String>,
        /** Unix seconds; 0 when the request has none. */
        val validUntil: Long,
        val requestedNetworkValue: Int?,
        val fromAccountId: String?,
    )

    fun validate(input: Input, now: Long): TonConnectSignMessageError? {
        if (!input.isFeatureEnabled || !input.isWalletSupported) {
            return TonConnectSignMessageError.NotSupported
        }
        if (input.messageAddresses.isEmpty() ||
            input.messageAddresses.size > input.walletMaxMessages ||
            !input.messageAddresses.all { it.isFriendlyAddress() }
        ) {
            return TonConnectSignMessageError.InvalidMessages
        }
        if (input.validUntil > 0 && input.validUntil <= now) {
            return TonConnectSignMessageError.Expired
        }
        val requestedNetwork = input.requestedNetworkValue
        if (requestedNetwork != null && requestedNetwork != input.walletNetworkValue) {
            return TonConnectSignMessageError.NetworkMismatch
        }
        val from = input.fromAccountId
        if (from != null && !from.equalsRawAddress(input.walletAccountId)) {
            return TonConnectSignMessageError.AddressMismatch
        }
        return null
    }

    /**
     * `valid_until` to sign: the dApp's value capped at [MAX_TTL] from now, [DEFAULT_TTL] when
     * absent (0), null when it has already passed.
     */
    fun signValidUntil(requestedValidUntil: Long, now: Long): Long? {
        if (requestedValidUntil <= 0) {
            return now + DEFAULT_TTL
        }
        if (requestedValidUntil <= now) {
            return null
        }
        return minOf(requestedValidUntil, now + MAX_TTL)
    }

    private fun String.isFriendlyAddress(): Boolean {
        return !contains(":") && isValidTonAddress()
    }
}

/**
 * Why [this] is refused before the sheet opens, or null. Expiry is judged by server time, as signing
 * judges it, so a skewed device clock neither refuses a live request nor lets an expired one reach
 * the sheet. [API.getServerTime] falls back to the device clock when offline.
 */
suspend fun SignRequestEntity.signMessageRejection(wallet: WalletEntity, api: API): TonConnectSignMessageError? {
    val now = withContext(Dispatchers.IO) { api.getServerTime(wallet.network).toLong() }
    return TonConnectSignMessageValidation.validate(toSignMessageInput(wallet), now)
}

fun SignRequestEntity.toSignMessageInput(wallet: WalletEntity): TonConnectSignMessageValidation.Input {
    return TonConnectSignMessageValidation.Input(
        isFeatureEnabled = WalletFeature.TonConnectSignMessage.isEnabled,
        isWalletSupported = wallet.isTonConnectSignMessageAvailable,
        walletMaxMessages = wallet.maxMessages,
        walletAccountId = wallet.accountId,
        walletNetworkValue = wallet.network.value,
        messageAddresses = messages.map { it.addressValue },
        validUntil = validUntil,
        requestedNetworkValue = requestedNetworkValue,
        fromAccountId = signMessageFromAccountId { from },
    )
}

/**
 * The account a request's `from` names. One that does not parse names no wallet, so it is answered
 * as a mismatch rather than thrown: over the HTTP bridge nothing above the validation catches it.
 */
internal fun signMessageFromAccountId(from: () -> AddrStd?): String? {
    return try {
        from()?.toAccountId()
    } catch (e: IllegalArgumentException) {
        ""
    }
}
