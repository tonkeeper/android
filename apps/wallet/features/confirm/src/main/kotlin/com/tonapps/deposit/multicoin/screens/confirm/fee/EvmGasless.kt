package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.chainkit.core.chain.model.account.TokenType
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import io.JsonAny
import io.batteryapi.models.GaslessV2EmulateRequest
import io.batteryapi.models.GaslessV2EstimateRequest
import io.batteryapi.models.GaslessV2Fee
import io.batteryapi.models.GaslessV2Payload
import io.batteryapi.models.GaslessV2SendRequest
import io.batteryapi.models.GaslessV2SendResponse
import io.infrastructure.ClientError
import io.infrastructure.ClientException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

class EvmGasless(
    private val api: API,
) {

    /** What the relayer is asked to carry: one call of the sender's, as the operation [type] it serves. */
    class Call(
        val type: Type,
        val chainId: String,
        val sender: String,
        val to: String,
        val value: BigInteger,
        val data: String,
    ) {

        enum class Type(val id: String) {
            Transfer("transfer"),
            Swap("swap"),
        }
    }

    class Estimation(
        val assetId: String,
        val fee: BigInteger,
        val available: Boolean,
    )

    class Rejection(
        val code: String?,
        val message: String?,
    ) {
        val isExpired: Boolean
            get() = code in EXPIRED_CODES

        val isAlreadySent: Boolean
            get() = code == ALREADY_SENT

        val isNothingSent: Boolean
            get() = code in NOT_SENT_CODES
    }

    class Emulation(
        val assetId: String,
        val fee: BigInteger,
        val operation: Operation,
        val expiresAt: Long,
    ) {
        val isExpired: Boolean
            get() = expiresAt - EXPIRY_LEAD_MS <= System.currentTimeMillis()
    }

    fun estimate(auth: Authorization, call: Call): List<Estimation> {
        val response = gasless().gaslessEstimate(
            gaslessV2EstimateRequest = GaslessV2EstimateRequest(
                chainId = call.chainId,
                senderAddress = call.sender,
                payload = call.payload(),
            ),
            xWalletId = auth.walletId,
        )
        return response.estimations.mapNotNull { estimation ->
            val fee = estimation.fee?.amount() ?: return@mapNotNull null
            Estimation(
                assetId = estimation.asset.id,
                fee = fee,
                available = estimation.available,
            )
        }
    }

    fun emulate(auth: Authorization, call: Call, assetId: String): Emulation {
        val response = gasless().gaslessEmulate(
            gaslessV2EmulateRequest = GaslessV2EmulateRequest(
                chainId = call.chainId,
                senderAddress = call.sender,
                type = call.type.id,
                gaslessAsset = assetId,
                payloads = listOf(call.payload()),
            ),
            xWalletId = auth.walletId,
        )
        if (response.assetId != assetId) {
            throw IllegalStateException("Emulated in ${response.assetId}, asked for $assetId")
        }
        val fee = response.fee.amount()
            ?: throw IllegalStateException("Unsupported emulation fee: ${response.fee.type.value}/${response.fee.data.amount}")
        val operation = response.operation.toOperation()
        // Only what a relayer submits is signed here: a `main` payload would come back as a
        // transaction the wallet pays for.
        val foreign = operation.payloads.firstOrNull { it.type !in RELAYED_TYPES }
        if (foreign != null) {
            throw IllegalStateException("Payload ${foreign.id} is `${foreign.type.id}`, not a relayer document")
        }
        return Emulation(
            assetId = assetId,
            fee = fee,
            operation = operation,
            expiresAt = response.expiresAt.toInstant().toEpochMilli(),
        )
    }

    fun send(auth: Authorization, operation: SignedOperation): GaslessV2SendResponse {
        return gasless().gaslessSend(
            gaslessV2SendRequest = GaslessV2SendRequest(
                operation = operation.toJson().getValue(OPERATION).jsonObject,
            ),
            xWalletId = auth.walletId,
        )
    }

    private fun gasless() = api.batteryGasless(TonNetwork.MAINNET)

    private fun Call.payload() = GaslessV2Payload(
        id = PAYLOAD_ID,
        type = PAYLOAD_TYPE,
        chainId = chainId,
        version = Payload.VERSION,
        meta = mapOf(
            "to" to JsonAny.Primitive(to),
            "value" to JsonAny.Primitive(value.toString()),
            "data" to JsonAny.Primitive(data),
        ),
    )

    private fun GaslessV2Fee.amount(): BigInteger? {
        if (type != GaslessV2Fee.Type.VALUE) {
            return null
        }
        return runCatching { BigInteger.parseString(data.amount) }.getOrNull()
    }

    private fun JsonObject.toOperation(): Operation {
        val id = string("id") ?: throw IllegalStateException("Operation has no id")
        return Operation.parse(id, toString()).getOrNull()
            ?: throw IllegalStateException("Operation $id cannot be read")
    }

    companion object {
        private const val PAYLOAD_ID = "p_01"
        private const val PAYLOAD_TYPE = "main"
        private const val OPERATION = "operation"
        private const val EMPTY_DATA = "0x"
        private const val TRANSFER_SELECTOR = "a9059cbb"
        private const val WORD_HEX = 64
        private const val ADDRESS_HEX = 40
        private const val EXPIRY_LEAD_MS = 5_000L

        private val RELAYED_TYPES = setOf(
            Payload.Type.Eip7702Authorization,
            Payload.Type.Eip7821HermesDelegate,
        )
        private val EXPIRED_CODES = setOf("emulation_expired", "operation_not_found")
        private const val ALREADY_SENT = "already_sent"
        private val NOT_SENT_CODES = setOf(
            "invalid_signature",
            "payload_mismatch",
            "unsupported_chain",
            "unsupported_asset",
            "unsupported_payload",
            "emulation_failed",
            "bad_request",
            "unauthorized",
            "insufficient_funds",
            "not_implemented",
        )

        fun transfer(
            account: AccountWithDetails,
            to: String,
            amount: BigInteger,
            type: Call.Type = Call.Type.Transfer,
        ): Call? {
            val chainId = chainId(account) ?: return null
            val sender = account.data.displayAddress
            return when (val asset = account.asset.value) {
                is Asset.Coin -> Call(
                    type = type,
                    chainId = chainId,
                    sender = sender,
                    to = to,
                    value = amount,
                    data = EMPTY_DATA,
                )

                is Asset.Token -> {
                    if (asset.type != TokenType.Erc20 && asset.type != TokenType.Bep20) {
                        return null
                    }
                    val data = erc20Transfer(to, amount) ?: return null
                    Call(
                        type = type,
                        chainId = chainId,
                        sender = sender,
                        to = asset.contract.display,
                        value = BigInteger.ZERO,
                        data = data,
                    )
                }

                is Asset.Battery -> null
            }
        }

        /** A route with calldata is that call as it stands; one without is a transfer to its address. */
        fun swap(account: AccountWithDetails, swap: Transaction.Swap): Call? {
            val data = swap.data?.takeIf { it.isNotBlank() }
                ?: return transfer(account, swap.to.display, swap.amount, Call.Type.Swap)
            val chainId = chainId(account) ?: return null
            return Call(
                type = Call.Type.Swap,
                chainId = chainId,
                sender = account.data.displayAddress,
                to = swap.to.display,
                value = swap.amount,
                data = data,
            )
        }

        fun isExpired(e: ClientException): Boolean {
            return rejection(e).isExpired
        }

        fun rejection(e: ClientException): Rejection {
            val body = (e.response as? ClientError<*>)?.body?.toString()
            val error = body?.let {
                runCatching { Json.parseToJsonElement(it).jsonObject["error"]?.jsonObject }.getOrNull()
            }
            return Rejection(
                code = error?.string("code"),
                message = error?.string("message"),
            )
        }

        private fun chainId(account: AccountWithDetails): String? {
            return chainId(account.asset.value.chain)
        }

        /** [chain] as the relayer names it, or null off EVM mainnets. */
        fun chainId(chain: Chain): String? {
            if (!chain.isEvm || chain.network.mode != Network.Mode.Mainnet) {
                return null
            }
            // The relayer's own spelling; CAIP-2 (`eip155:1`) is rejected.
            return "${chain.network.type.id}/${chain.network.mode.id}"
        }

        private fun erc20Transfer(to: String, amount: BigInteger): String? {
            val address = to.removePrefix("0x").lowercase()
            if (address.length != ADDRESS_HEX || address.any { it !in '0'..'9' && it !in 'a'..'f' }) {
                return null
            }
            val value = amount.toString(16)
            if (amount.signum() < 0 || value.length > WORD_HEX) {
                return null
            }
            return "0x" + TRANSFER_SELECTOR + address.padStart(WORD_HEX, '0') + value.padStart(WORD_HEX, '0')
        }

        private fun JsonObject.string(key: String): String? {
            return (this[key] as? JsonPrimitive)?.contentOrNull
        }
    }
}
