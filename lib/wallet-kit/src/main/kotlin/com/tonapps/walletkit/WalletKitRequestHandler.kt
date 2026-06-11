package com.tonapps.walletkit

import io.ton.walletkit.api.generated.TONSendTransactionApprovalResponse
import io.ton.walletkit.api.generated.TONSignDataApprovalResponse
import io.ton.walletkit.model.TONBase64
import io.ton.walletkit.model.TONHex
import io.ton.walletkit.request.TONWalletSignDataRequest
import io.ton.walletkit.request.TONWalletTransactionRequest

/**
 * Handles WalletKit SDK transaction and sign-data requests.
 * Keeps all SDK response construction logic in the :lib:wallet-kit module.
 */
object WalletKitRequestHandler {

    /**
     * Result returned from a sign-data UI callback.
     */
    data class SignDataProof(
        val signatureBytes: ByteArray,
        val timestamp: Long,
        val domainValue: String,
    )

    /**
     * Handles a WalletKit send-transaction request:
     * 1. Serializes the SDK event to JSON
     * 2. Validates expiration
     * 3. Delegates signing to [sign] (which shows UI)
     * 4. Approves or rejects the SDK request
     *
     * On success, returns normally.
     * On any failure, calls [TONWalletTransactionRequest.reject] and throws.
     */
    suspend fun handleTransactionRequest(
        request: TONWalletTransactionRequest,
        sign: suspend (signRequestJson: String) -> String,
    ) {
        val json = try {
            WalletKitEventMapper.serializeTransactionRequest(request.event)
        } catch (e: Exception) {
            request.reject("Failed to parse transaction request")
            throw e
        }

        try {
            val boc = sign(json)
            request.approve(
                TONSendTransactionApprovalResponse(signedBoc = TONBase64(boc))
            )
        } catch (e: Throwable) {
            request.reject(e.message ?: "Unknown error")
            throw e
        }
    }

    /**
     * Handles a WalletKit sign-data request:
     * 1. Serializes the SDK event to JSON
     * 2. Delegates signing to [sign] (which parses payload and shows UI)
     * 3. Approves or rejects the SDK request
     *
     * On success, returns normally.
     * On any failure, calls [TONWalletSignDataRequest.reject] and throws.
     */
    suspend fun handleSignDataRequest(
        request: TONWalletSignDataRequest,
        sign: suspend (payloadJson: String) -> SignDataProof,
    ) {
        val json = try {
            WalletKitEventMapper.serializeSignDataPayload(request.event)
        } catch (e: Exception) {
            request.reject("Failed to parse sign data request")
            throw e
        }

        try {
            val proof = sign(json)
            request.approve(
                TONSignDataApprovalResponse(
                    signature = TONHex.fromData(proof.signatureBytes, withPrefix = true),
                    timestamp = proof.timestamp.toInt(),
                    domain = proof.domainValue,
                )
            )
        } catch (e: Throwable) {
            request.reject(e.message ?: "Unknown error")
            throw e
        }
    }
}
