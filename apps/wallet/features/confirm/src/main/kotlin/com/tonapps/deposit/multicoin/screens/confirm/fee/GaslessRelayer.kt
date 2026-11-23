package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.solana.SolanaTransaction
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.TonSendMode
import org.ton.tlb.loadTlb
import org.ton.block.Coins
import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.toBigInteger
import com.tonapps.blockchain.tron.TronTransactionRequest
import com.tonapps.blockchain.tron.TronTransaction
import com.tonapps.blockchain.ton.extensions.cellFromBase64
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.asCellRef
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.chainkit.core.chain.model.account.TokenType
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.op.Intent
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.chainkit.core.op.toJson
import com.tonapps.log.L
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import io.JsonAny
import io.JsonAnySerializer
import io.Serializer
import io.batteryapi.models.GaslessV2EmulateRequest
import io.batteryapi.models.GaslessV2EstimateRequest
import io.batteryapi.models.GaslessV2Fee
import io.batteryapi.models.GaslessV2Payload
import io.batteryapi.models.GaslessV2SendRequest
import io.batteryapi.models.GaslessV2SendResponse
import io.infrastructure.ClientError
import io.infrastructure.ClientException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** The gasless relayer's Generic Payload API, on the EVM chains, Solana, TRON and TON it carries. */
class GaslessRelayer(
    private val api: API,
) {

    /**
     * What the relayer is asked to carry: one call of the sender's, as the operation [type] it serves.
     * An EVM call is [to], [value] and [data]; a Solana one is the [solana] payload chainkit describes it
     * as, a transfer or a provider's prebuilt transaction, and the [intent] the user agreed to, which the
     * transaction the relayer builds of it is held to. A TRON one is the [tron] transaction that makes the
     * call, which the relayer hands back to sign as it is. A TON one is the [ton] message, which the
     * relayer hands back to sign for its own delivery, held to the [intent] too.
     */
    class Call(
        val type: Type,
        val chainId: String,
        val sender: String,
        val to: String,
        val value: BigInteger,
        val data: String? = null,
        val solana: PayloadMeta.Solana? = null,
        // An EVM `main` as the swap states it, every field of it.
        val evm: PayloadMeta.Evm.Main? = null,
        val tron: PayloadMeta.Tron? = null,
        // What the relayer's TRON transaction is held to, for a `main` asked as it is.
        val tronRequest: TronTransactionRequest? = null,
        val ton: PayloadMeta.Ton.Main? = null,
        val intent: Intent? = null,
    ) {

        enum class Type(val id: String) {
            Transfer("transfer"),
            Swap("swap"),
        }
    }

    /** What an operation is for: the relayer's operation type, and the recipient a transfer is held to. */
    class Purpose(val type: Call.Type, val recipient: Address?) {

        companion object {
            val Swap = Purpose(Call.Type.Swap, recipient = null)

            fun transfer(recipient: Address) = Purpose(Call.Type.Transfer, recipient)
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
        /** Epoch millis on the relayer's clock. */
        val expiresAt: Long,
        /** The wallet seqno the relayer delivers each TON message at, by payload id, where it says. */
        val tonSeqnos: Map<String, Long> = emptyMap(),
        /** [operation] as the user's intent holds it: a TON batch without the fee the relayer adds to it. */
        val intended: Operation = operation,
        /** The deadline the relayer states for each TON payload, unix seconds, by payload id. */
        val tonDeadlines: Map<String, Long> = emptyMap(),
    ) {
        fun isExpired(serverNowMs: Long): Boolean = expiresAt - EXPIRY_LEAD_MS <= serverNowMs
    }

    fun estimate(auth: Authorization, call: Call): List<Estimation> {
        val response = gasless().gaslessEstimate(
            gaslessV2EstimateRequest = GaslessV2EstimateRequest(
                chainId = call.chainId,
                senderAddress = call.sender,
                payload = call.payload(payloadId(0)),
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

    /**
     * [feeWallet] is the account's own wallet of the jetton [assetId] names, where the fee is paid in a TON
     * jetton: the relayer answers a TON operation so paid with one batch of the messages asked for and the
     * jetton's transfer of the fee, out of that wallet.
     */
    fun emulate(auth: Authorization, calls: List<Call>, assetId: String, feeWallet: String? = null): Emulation {
        val call = calls.first()
        val response = gasless().gaslessEmulate(
            gaslessV2EmulateRequest = GaslessV2EmulateRequest(
                chainId = call.chainId,
                senderAddress = call.sender,
                type = call.type.id,
                gaslessAsset = assetId,
                payloads = calls.mapIndexed { index, item -> item.payload(payloadId(index)) },
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
        val delivered = response.operation.deliveredByRelayer()
        val batch = operation.payloads.singleOrNull()?.takeIf { call.ton != null && it.meta is PayloadMeta.Ton.Batch }
        if (batch != null) {
            val intended = tonBatch(batch, calls, delivered, assetId, fee, feeWallet)
            val tonSeqnos = response.operation.tonSeqnos()
            requireConsecutive(listOf(batch.id), tonSeqnos)
            return Emulation(
                assetId = assetId,
                fee = fee,
                operation = operation,
                expiresAt = response.expiresAt.toInstant().toEpochMilli(),
                tonSeqnos = tonSeqnos,
                intended = operation.copy(payloads = listOf(intended)),
                tonDeadlines = response.operation.tonDeadlines(),
            )
        }
        // Each TON message asked for is answered by one, in the order asked: none more is signed.
        if (call.ton != null && operation.payloads.size != calls.size) {
            throw IllegalStateException("${operation.payloads.size} payloads answer ${calls.size} TON messages")
        }
        val foreign = if (call.ton != null) {
            operation.payloads.zip(calls).firstOrNull { (payload, asked) -> !payload.isRelayed(asked, delivered) }?.first
        } else {
            operation.payloads.firstOrNull { !it.isRelayed(call, delivered) }
        }
        if (foreign != null) {
            throw IllegalStateException("Payload ${foreign.id} is `${foreign.type.id}`, not a relayer document")
        }
        val tonSeqnos = response.operation.tonSeqnos()
        if (call.ton != null) {
            requireConsecutive(operation.payloads.map { it.id }, tonSeqnos)
        }
        if (calls.none { it.solana != null || it.tron != null || it.ton != null }) {
            GaslessExecuteReader.read(operation, calls, assetId, fee)
        }
        return Emulation(
            assetId = assetId,
            fee = fee,
            operation = operation,
            expiresAt = response.expiresAt.toInstant().toEpochMilli(),
            tonSeqnos = tonSeqnos,
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

    /** An operation the battery built, as `/purchase-battery/emulate` answers it, to sign as any other. */
    fun parseOperation(json: JsonObject): Operation = json.toOperation()

    private fun gasless() = api.batteryGasless(TonNetwork.MAINNET)

    private fun payloadId(index: Int): String = PAYLOAD_ID_PREFIX + (index + 1).toString().padStart(2, '0')

    private fun Call.payload(id: String): GaslessV2Payload {
        // A Solana payload goes as chainkit writes it: the Generic Payload format is the relayer's too.
        val solana = solana
        if (solana != null) {
            val meta = Serializer.JSON.decodeFromJsonElement(JsonAnySerializer, solana.toJson()).asObject().orEmpty()
            return payload(id, solana.payloadType.id, meta)
        }

        // A TON message goes as chainkit writes it too.
        val ton = ton
        if (ton != null) {
            val meta = Serializer.JSON.decodeFromJsonElement(JsonAnySerializer, ton.toJson()).asObject().orEmpty()
            return payload(id, Payload.Type.Main.id, meta)
        }

        // A TRON `main` goes as chainkit writes it; a route's own transaction as its document, in a string.
        when (val tron = tron) {
            is PayloadMeta.Tron.Main -> {
                val meta = Serializer.JSON.decodeFromJsonElement(JsonAnySerializer, tron.toJson()).asObject().orEmpty()
                return payload(id, Payload.Type.Main.id, meta)
            }

            is PayloadMeta.Tron.Prebuild -> return payload(id, Payload.Type.Prebuild.id, mapOf(DATA to JsonAny.Primitive(tron.data)))

            else -> Unit
        }

        // An EVM `main` of a swap goes as chainkit writes it, gas and authorization with it.
        val evm = evm
        if (evm != null) {
            val meta = Serializer.JSON.decodeFromJsonElement(JsonAnySerializer, evm.toJson()).asObject().orEmpty()
            return payload(id, Payload.Type.Main.id, meta)
        }

        val meta = mapOf(
            "to" to to,
            "value" to value.toString(),
            "data" to (data ?: EMPTY_DATA),
        )
        return payload(id, PAYLOAD_TYPE, meta.mapValues { (_, value) -> JsonAny.Primitive(value) })
    }

    private fun Call.payload(id: String, type: String, meta: Map<String, JsonAny>) = GaslessV2Payload(
        id = id,
        type = type,
        chainId = chainId,
        version = Payload.VERSION,
        meta = meta,
    )

    // A Solana relayer document is a transaction the relayer pays the fee of; one the sender would pay
    // for is not relayed at all. A TRON one is the sender's own transaction, which the relayer rents the
    // energy for: it is the transaction asked for, to the byte, or it is not signed.
    private fun Payload.isRelayed(call: Call, delivered: Set<String>): Boolean {
        return when (val meta = meta) {
            is PayloadMeta.Solana.Prebuild -> {
                val payer = SolanaTransaction.feePayer(meta.data)
                payer != null && payer != call.sender
            }

            // A TRON relayer document is the sender's own transaction: a route's, to the byte, or the one
            // that makes the `main` asked for.
            is PayloadMeta.Tron.Prebuild -> {
                val answered = tronRawData(meta.data)
                when (val asked = call.tron) {
                    is PayloadMeta.Tron.Prebuild -> {
                        val raw = tronRawData(asked.data)
                        raw != null && raw.equals(answered, ignoreCase = true)
                    }

                    is PayloadMeta.Tron.Main -> {
                        val request = call.tronRequest
                        answered != null && request != null &&
                            runCatching { TronTransaction.fromRawDataHex(answered, request) }.isSuccess
                    }

                    else -> false
                }
            }

            // A TON relayer document is the message asked for, [delivered] by the relayer: the same coins
            // to the same contract, with the same body, state init and jetton. One the wallet would send
            // itself is not. A jetton deposit is not signed here: the wallet's transfer of it is chainkit's
            // to make (tonkeeper/chain-kit#89).
            is PayloadMeta.Ton.Main -> {
                val asked = call.ton
                asked != null && id in delivered && meta.answers(asked)
            }

            else -> type in RELAYED_TYPES
        }
    }

    // The message asked for, as the relayer states it back: the same coins to the same contract, with the
    // same body, state init and bounce, and no jetton.
    private fun PayloadMeta.Ton.Main.answers(asked: PayloadMeta.Ton.Main): Boolean {
        return asked.token == null &&
            token == null &&
            value == asked.value &&
            to.display.equalsAddress(asked.to.display) &&
            sameCell(deliveredBody(this), deliveredBody(asked)) &&
            sameCell(stateInit, asked.stateInit) &&
            (bounce == null || bounce == deliveredBounce(asked)) &&
            (mode ?: DELIVERED_MODE) == (asked.mode ?: DELIVERED_MODE)
    }

    /**
     * A TON batch the relayer delivers: the messages asked for, in the order asked, then, where the fee is a
     * jetton's, that jetton's transfer of [fee] out of [feeWallet]. What comes back is the batch without the
     * fee, as the user's intent holds it.
     */
    private fun tonBatch(
        payload: Payload,
        calls: List<Call>,
        delivered: Set<String>,
        assetId: String,
        fee: BigInteger,
        feeWallet: String?,
    ): Payload {
        val meta = payload.meta as PayloadMeta.Ton.Batch
        val messages = meta.calls.map { it as? PayloadMeta.Ton.Main ?: throw IllegalStateException("A TON batch carries a `${it::class.simpleName}`") }
        val asked = calls.map { it.ton ?: throw IllegalStateException("A TON batch answers a call that is not TON") }
        if (payload.id !in delivered || messages.size < asked.size) {
            throw IllegalStateException("Payload ${payload.id} is no batch the relayer delivers of ${asked.size} messages")
        }

        val answered = messages.take(asked.size)
        if (answered.zip(asked).any { (message, wanted) -> !message.answers(wanted) }) {
            throw IllegalStateException("Payload ${payload.id} is not the messages asked for")
        }

        // Refused here, before it is offered, as it is again when signed: see [carriesAll].
        if (messages.dropLast(1).any { carriesAll(it.mode) }) {
            throw IllegalStateException("Payload ${payload.id} sends a message after one that carries all")
        }

        val fees = messages.drop(asked.size)
        val paid = if (assetId == Asset.Battery.ID) {
            fees.isEmpty()
        } else {
            fees.singleOrNull()?.isJettonFee(fee, feeWallet) == true
        }
        if (!paid) {
            throw IllegalStateException("Payload ${payload.id} pays the relayer otherwise than $fee of $assetId")
        }

        return payload.copy(meta = meta.copy(calls = answered))
    }

    // The jetton's transfer of [fee] out of [wallet], the account's own wallet of it, with little TON of its own:
    // in no mode past the delivered one's bits, gas paid apart and errors ignored. None other carries the wallet's
    // balance or the relayer's attachment, destroys the wallet or fails the action phase.
    private fun PayloadMeta.Ton.Main.isJettonFee(fee: BigInteger, wallet: String?): Boolean {
        if (wallet == null || token != null || !stateInit.isNullOrBlank() || value > MAX_FEE_FORWARD) {
            return false
        }
        if ((mode ?: DELIVERED_MODE) and DELIVERED_MODE.inv() != 0) {
            return false
        }
        if (!to.display.equalsAddress(wallet)) {
            return false
        }

        val transferred = runCatching {
            val slice = body!!.cellFromBase64().beginParse()
            if (slice.loadUInt(OP_BITS).toLong() != TONOpCode.JETTON_TRANSFER.code) {
                return false
            }
            slice.loadUInt(QUERY_ID_BITS)
            slice.loadTlb(Coins).amount.value.toBigInteger()
        }.getOrNull() ?: return false
        // The jetton's own amount, as a number: a java one to the relayer's.
        return transferred.toString() == fee.toString()
    }

    private fun GaslessV2Fee.amount(): BigInteger? {
        if (type != GaslessV2Fee.Type.VALUE) {
            return null
        }
        val amount = data.amount
        if (amount == null) {
            L.w("Gasless fee: a value fee without an amount")
            return null
        }
        return runCatching { BigInteger.parseString(amount) }.getOrNull()
    }

    /**
     * The ids of the TON messages the relayer delivers inside a message of its own, rather than the
     * wallet in one it pays for. The relayer names that `signature_type: "internal"`, the Generic Payload
     * `format: "int_msg"`: either is read off the answer itself, chainkit's `main` carries neither.
     */
    private fun JsonObject.deliveredByRelayer(): Set<String> {
        val payloads = this[PAYLOADS] as? JsonArray ?: return emptySet()
        return payloads.mapNotNull { payload ->
            val item = payload as? JsonObject ?: return@mapNotNull null
            val meta = item[META] as? JsonObject ?: return@mapNotNull null
            val internal = meta.string(SIGNATURE_TYPE) == SIGNED_INTERNAL || meta.string(FORMAT) == FORMAT_INT_MSG
            item.string(ID).takeIf { internal }
        }.toSet()
    }

    // The parts of a TON route go one after another: at seqnos the relayer states for all of them, each the
    // one after the last, or at none, when they follow the wallet's own.
    private fun requireConsecutive(ids: List<String>, seqnos: Map<String, Long>) {
        val stated = ids.mapNotNull { seqnos[it] }
        if (stated.isEmpty()) {
            return
        }

        val first = stated.first()
        val consecutive = stated.size == ids.size && stated.withIndex().all { (index, seqno) -> seqno == first + index }
        if (!consecutive || first < 0 || first > Int.MAX_VALUE - (ids.size - 1)) {
            throw IllegalStateException("TON seqnos $stated are not one after another for ${ids.size} messages")
        }
    }

    // The seqno each TON message is to be signed at, as the relayer states it: chainkit's `main` drops it.
    private fun JsonObject.tonSeqnos(): Map<String, Long> {
        val payloads = this[PAYLOADS] as? JsonArray ?: return emptyMap()
        return payloads.mapNotNull { payload ->
            val item = payload as? JsonObject ?: return@mapNotNull null
            val meta = item[META] as? JsonObject ?: return@mapNotNull null
            val id = item.string(ID) ?: return@mapNotNull null
            val seqno = meta.string(SEQNO)?.toLongOrNull() ?: return@mapNotNull null
            id to seqno
        }.toMap()
    }

    // The deadline each TON payload states, a batch's on the batch: chainkit's batch drops it.
    private fun JsonObject.tonDeadlines(): Map<String, Long> {
        val payloads = this[PAYLOADS] as? JsonArray ?: return emptyMap()
        return payloads.mapNotNull { payload ->
            val item = payload as? JsonObject ?: return@mapNotNull null
            val meta = item[META] as? JsonObject ?: return@mapNotNull null
            val id = item.string(ID) ?: return@mapNotNull null
            val deadline = meta.string(EXPIRE_AT)?.toLongOrNull() ?: return@mapNotNull null
            id to deadline
        }.toMap()
    }

    // The same cell whatever its serialization: a BOC with or without its checksum is one body.
    private fun sameCell(answered: String?, asked: String?): Boolean {
        if (answered.isNullOrBlank() || asked.isNullOrBlank()) {
            return answered.isNullOrBlank() && asked.isNullOrBlank()
        }
        val hashes = runCatching { answered.cellFromBase64().hash() to asked.cellFromBase64().hash() }.getOrNull()
        return hashes != null && hashes.first == hashes.second
    }

    private fun tronRawData(document: String): String? {
        return runCatching { Json.parseToJsonElement(document).jsonObject.string(RAW_DATA_HEX) }.getOrNull()
    }

    private fun JsonObject.toOperation(): Operation {
        val id = string("id") ?: throw IllegalStateException("Operation has no id")
        return Operation.parse(id, toString()).getOrNull()
            ?: throw IllegalStateException("Operation $id cannot be read")
    }

    companion object {
        private const val PAYLOAD_ID_PREFIX = "p_"
        private const val PAYLOAD_TYPE = "main"
        private const val OPERATION = "operation"
        private const val EMPTY_DATA = "0x"
        private const val DATA = "data"
        private const val RAW_DATA_HEX = "raw_data_hex"
        private const val PAYLOADS = "payloads"
        private const val META = "meta"
        private const val ID = "id"
        private const val OP_BITS = 32
        private const val QUERY_ID_BITS = 64

        // What TON a fee's jetton transfer may carry for its gas.
        private val MAX_FEE_FORWARD = BigInteger.fromInt(100_000_000)
        private const val SEQNO = "seqno"

        /** The send mode a TON message goes in where it states none: its gas paid apart, errors ignored. */
        const val DELIVERED_MODE = 3
        private val CARRIES_ALL = TonSendMode.CARRY_ALL_REMAINING_BALANCE.value or TonSendMode.CARRY_ALL_REMAINING_INCOMING_VALUE.value
        private const val EXPIRE_AT = "expire_at"
        private const val HEX_PREFIX = "0x"

        // What a call may burn of the sender's TRX, as the wallet's own TRC-20 transfers allow.
        private const val MAX_TRON_FEE_LIMIT = 150_000_000L

        private const val USER_FRIENDLY_SIZE = 36
        private const val TAG_MASK = 0x7f
        private const val BOUNCEABLE_TAG = 0x11
        private const val NON_BOUNCEABLE_TAG = 0x51
        private const val FORMAT = "format"
        private const val FORMAT_INT_MSG = "int_msg"
        private const val SIGNATURE_TYPE = "signature_type"
        private const val SIGNED_INTERNAL = "internal"
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
            val chainId = evmChainId(account) ?: return null
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

        /**
         * A route with calldata is that call as it stands; one without is a transfer to its address.
         * On Solana the calldata is the provider's transaction, carried as it is; a deposit with none is
         * the transfer it pays, memo and all.
         */
        fun swap(account: AccountWithDetails, swap: Transaction.Swap): Call? {
            val data = swap.data?.takeIf { it.isNotBlank() }
            val chain = account.asset.value.chain
            if (chain is Chain.Solana) {
                data ?: return solanaTransfer(account, swap.to.display, swap.amount, swap.memo, Call.Type.Swap)
                val chainId = chainId(chain) ?: return null
                return Call(
                    type = Call.Type.Swap,
                    chainId = chainId,
                    sender = account.data.displayAddress,
                    to = swap.to.display,
                    value = swap.amount,
                    solana = PayloadMeta.Solana.Prebuild(data),
                    intent = Intent(listOf(Intent.Spend(account.asset.value, swap.amount)), swap.to, Intent.Source.Wallet),
                )
            }
            data ?: return transfer(account, swap.to.display, swap.amount, Call.Type.Swap)
            val chainId = evmChainId(account) ?: return null
            return Call(
                type = Call.Type.Swap,
                chainId = chainId,
                sender = account.data.displayAddress,
                to = swap.to.display,
                value = swap.amount,
                data = data,
            )
        }

        /** An EVM `main` of a swap as it states itself: what the relayer runs is held to its address, value and data. */
        fun evmMain(account: AccountWithDetails, meta: PayloadMeta.Evm.Main, purpose: Purpose = Purpose.Swap): Call? {
            val chainId = evmChainId(account) ?: return null
            return Call(
                type = purpose.type,
                chainId = chainId,
                sender = account.data.displayAddress,
                to = meta.to.display,
                value = meta.value,
                data = meta.data?.takeIf { it.isNotBlank() } ?: EMPTY_DATA,
                evm = meta,
            )
        }

        fun call(account: AccountWithDetails, to: String, value: BigInteger, data: String?): Call? {
            val chainId = evmChainId(account) ?: return null
            return Call(
                type = Call.Type.Swap,
                chainId = chainId,
                sender = account.data.displayAddress,
                to = to,
                value = value,
                data = data?.takeIf { it.isNotBlank() } ?: EMPTY_DATA,
            )
        }

        /**
         * A Solana swap as its payload states it: a deposit the relayer makes the transfer of, held to
         * the recipient and the amount it names, or the provider's transaction, held to [sold] of the
         * account's asset. Null for what is not a transaction.
         */
        fun solanaSwap(account: AccountWithDetails, meta: PayloadMeta.Solana, sold: BigInteger, purpose: Purpose = Purpose.Swap): Call? {
            val asset = account.asset.value
            val chain = asset.chain
            if (chain !is Chain.Solana) {
                return null
            }
            val chainId = chainId(chain) ?: return null

            val intent = when (meta) {
                is PayloadMeta.Solana.Main -> Intent(listOf(Intent.Spend(asset, meta.value)), meta.to, Intent.Source.Wallet)
                is PayloadMeta.Solana.Prebuild -> Intent(listOf(Intent.Spend(asset, sold)), purpose.recipient, Intent.Source.Wallet)
                is PayloadMeta.Solana.PlainMessage -> return null
            }
            val deposit = meta as? PayloadMeta.Solana.Main
            return Call(
                type = purpose.type,
                chainId = chainId,
                sender = account.data.displayAddress,
                to = deposit?.to?.display.orEmpty(),
                value = deposit?.value ?: BigInteger.ZERO,
                solana = meta,
                intent = intent,
            )
        }

        /**
         * A TON swap as its message states it, held to [sold] of the account's asset. The relayer hands
         * the message back to sign as a request it delivers itself, the excess of what it attaches sent
         * its way.
         */
        /** The body a TON message is sent with: its own, else its comment as a text comment. */
        fun deliveredBody(meta: PayloadMeta.Ton.Main): String? {
            return meta.body?.takeIf { it.isNotBlank() }
                ?: meta.comment?.takeIf { it.isNotEmpty() }?.let { asCellRef(it)?.base64() }
        }

        /**
         * Whether a message in [mode] carries the whole balance or all that came in: none may come after it,
         * for it leaves them nothing, and mode 3 skips them unseen, the relayer's fee among them.
         */
        fun carriesAll(mode: Int?): Boolean {
            return (mode ?: DELIVERED_MODE) and CARRIES_ALL != 0
        }

        /**
         * Whether the TON message asked for is sent bounceable, as it is signed: its own flag, else its
         * address's user-friendly tag, else bounceable, as a raw address is.
         */
        fun deliveredBounce(meta: PayloadMeta.Ton.Main): Boolean {
            return meta.bounce ?: bounceableTag(meta.to.display) ?: true
        }

        // The tag byte of a user-friendly address: 0x11 bounceable, 0x51 not, either with the testnet bit.
        private fun bounceableTag(address: String): Boolean? {
            if (address.contains(':')) {
                return null
            }
            val bytes = runCatching { java.util.Base64.getUrlDecoder().decode(address.replace('+', '-').replace('/', '_')) }
                .getOrNull()
                ?.takeIf { it.size == USER_FRIENDLY_SIZE }
                ?: return null
            return when (bytes[0].toInt() and TAG_MASK) {
                BOUNCEABLE_TAG -> true
                NON_BOUNCEABLE_TAG -> false
                else -> null
            }
        }

        fun tonSwap(account: AccountWithDetails, meta: PayloadMeta.Ton.Main, sold: BigInteger, purpose: Purpose = Purpose.Swap): Call? {
            val asset = account.asset.value
            val chain = asset.chain
            if (!chain.isTvm) {
                return null
            }
            val chainId = chainId(chain) ?: return null
            return Call(
                type = purpose.type,
                chainId = chainId,
                sender = account.data.displayAddress,
                to = meta.to.display,
                value = meta.value,
                ton = meta,
                // A transfer is held to its recipient as well as its amount.
                intent = Intent(listOf(Intent.Spend(asset, sold)), purpose.recipient, Intent.Source.Wallet),
            )
        }

        /**
         * A TRON call as [transaction], the document of the transaction that makes it. It carries no
         * intent: the transaction is that of an operation already held to one, and what the relayer hands
         * back to sign is that transaction or nothing.
         */
        /**
         * A TRON `main` as the swap states it: a call of [PayloadMeta.Tron.Main.to] with its `data`, or TRX
         * to it, with its memo. The relayer builds the transaction, held to that `main` before it is signed.
         */
        fun tronMain(account: AccountWithDetails, meta: PayloadMeta.Tron.Main, purpose: Purpose = Purpose.Swap): Call? {
            val chain = account.asset.value.chain
            if (chain !is Chain.Tron) {
                return null
            }
            val chainId = chainId(chain) ?: return null

            val from = account.data.displayAddress
            val sun = meta.value.longValue(exactRequired = true)
            // The memo is hex, as the Generic Payload sends it and chainkit reads it.
            val memo = meta.memo?.takeIf { it.isNotBlank() }
            val data = meta.data?.removePrefix(HEX_PREFIX)?.takeIf { it.isNotBlank() }
            // A `main` with no calldata is TRX, as chainkit signs it: a sale of a token cannot be one.
            if (data == null && account.asset.value !is Asset.Coin) {
                return null
            }
            val request = if (data == null) {
                TronTransactionRequest.Trx(from = from, to = meta.to.display, amountSun = sun, memo = memo)
            } else {
                TronTransactionRequest.Call(
                    from = from,
                    contractAddress = meta.to.display,
                    callData = data,
                    feeLimit = MAX_TRON_FEE_LIMIT,
                    callValue = sun,
                    memo = memo,
                )
            }

            return Call(
                type = purpose.type,
                chainId = chainId,
                sender = from,
                to = meta.to.display,
                value = meta.value,
                tron = meta,
                tronRequest = request,
            )
        }

        fun tronCall(account: AccountWithDetails, transaction: String, purpose: Purpose = Purpose.Swap): Call? {
            val chain = account.asset.value.chain
            if (chain !is Chain.Tron) {
                return null
            }
            val chainId = chainId(chain) ?: return null
            // The contract called and what goes with the call are the transaction's own.
            return Call(
                type = purpose.type,
                chainId = chainId,
                sender = account.data.displayAddress,
                to = "",
                value = BigInteger.ZERO,
                tron = PayloadMeta.Tron.Prebuild(transaction),
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

        /** A Solana transfer of [amount] base units to [to], the recipient wallet, with its [memo]. */
        fun solanaTransfer(
            account: AccountWithDetails,
            to: String,
            amount: BigInteger,
            memo: String?,
            type: Call.Type = Call.Type.Transfer,
        ): Call? {
            val asset = account.asset.value
            val chain = asset.chain
            if (chain !is Chain.Solana) {
                return null
            }
            val chainId = chainId(chain) ?: return null
            val token = when (asset) {
                is Asset.Coin -> null
                is Asset.Token -> asset.contract.takeIf { asset.type == TokenType.SPL } ?: return null
                is Asset.Battery -> return null
            }

            val recipient = Address.force(to, chain)
            return Call(
                type = type,
                chainId = chainId,
                sender = account.data.displayAddress,
                to = to,
                value = amount,
                solana = PayloadMeta.Solana.Main(
                    to = recipient,
                    value = amount,
                    memo = memo?.takeIf { it.isNotBlank() },
                    token = token,
                ),
                intent = Intent(listOf(Intent.Spend(asset, amount)), recipient, Intent.Source.Wallet),
            )
        }

        private fun evmChainId(account: AccountWithDetails): String? {
            return account.asset.value.chain.takeIf { it.isEvm }?.let(::chainId)
        }

        /** [chain] as the relayer names it, or null off EVM, Solana, TRON and TON mainnets. */
        fun chainId(chain: Chain): String? {
            val carried = chain.isEvm || chain is Chain.Solana || chain is Chain.Tron || chain.isTvm
            if (!carried || chain.network.mode != Network.Mode.Mainnet) {
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

// A provider's prebuilt transaction goes as `prebuild`, a transfer as `main`, for the relayer to build.
private val PayloadMeta.Solana.payloadType: Payload.Type
    get() = if (this is PayloadMeta.Solana.Prebuild) {
        Payload.Type.Prebuild
    } else {
        Payload.Type.Main
    }
