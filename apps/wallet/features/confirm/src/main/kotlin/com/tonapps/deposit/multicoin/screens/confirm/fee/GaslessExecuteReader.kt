package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

private const val HERMES_DELEGATE = "0x24b576bc271823bf9c24be627b4363b9b00191e9"
private const val HERMES_NAME = "Hermes"
private const val HERMES_VERSION = "v1.0.0"
private const val HEX_PREFIX = "0x"
private const val HEX_RADIX = 16
private const val WORD_HEX_LENGTH = 64
private const val ADDRESS_HEX_LENGTH = 40
private const val TRANSFER = "a9059cbb"
private const val TRANSFER_HEX_LENGTH = 8 + WORD_HEX_LENGTH * 2
private const val MODE_BYTES = 32
private const val CALL_TYPE_BATCH = 0x01
private const val EXEC_TYPE_DEFAULT = 0x00
private const val EXEC_TYPE_TRY = 0x01
private const val MODE_SELECTOR_OFFSET = 6
private const val MODE_PAYLOAD_OFFSET = 10
private const val POLICY_BITS = 2
private const val REVERTING_POLICY = 0b01
private const val INTERRUPTING_POLICY = 0b10
private const val TOKEN_ASSET_ID_PARTS = 4
private const val TYPES = "types"
private const val EIP712_DOMAIN = "EIP712Domain"
private const val EXECUTE = "Execute"
private const val CALL = "Call"
private const val PRIMARY_TYPE = "primaryType"
private const val DOMAIN = "domain"
private const val MESSAGE = "message"
private const val NAME = "name"
private const val TYPE = "type"
private const val VERSION = "version"
private const val CHAIN_ID = "chainId"
private const val VERIFYING_CONTRACT = "verifyingContract"
private const val SALT = "salt"
private const val MODE = "mode"
private const val CALLS = "calls"
private const val TARGET = "target"
private const val VALUE = "value"
private const val DATA = "data"
private val MODE_SELECTOR = byteArrayOf(0x78, 0x21, 0x00, 0x01)
private val UINT_DECIMAL = Regex("[0-9]{1,78}")
private val HEX_DIGITS = Regex("([0-9a-f]{2})*")
private val DOMAIN_TYPE = listOf(
    "name" to "string",
    "version" to "string",
    "chainId" to "uint256",
    "verifyingContract" to "address",
    "salt" to "bytes32",
)
private val EXECUTE_TYPE = listOf(
    "mode" to "bytes32",
    "calls" to "Call[]",
    "nonce" to "uint256",
    "deadline" to "uint256",
)
private val CALL_TYPE = listOf(
    "target" to "address",
    "value" to "uint256",
    "data" to "bytes",
)

internal object GaslessExecuteReader {

    fun read(operation: Operation, calls: List<GaslessRelayer.Call>, assetId: String, fee: BigInteger) {
        val first = calls.firstOrNull() ?: refuse("calls")
        val chain = Chain.find(first.chainId.substringBefore('/'), first.chainId.substringAfter('/')) as? Chain.Evm
            ?: refuse("chain")
        val sender = first.sender.lowercase()
        if (calls.any { it.chainId != first.chainId || !it.sender.equals(sender, ignoreCase = true) }) {
            refuse("calls")
        }
        if (operation.payloads.any { it.chain != chain || it.version != Payload.VERSION }) {
            refuse("payloads")
        }
        val authorizations = operation.payloads.filter { it.type == Payload.Type.Eip7702Authorization }
        val execute = operation.payloads.singleOrNull { it.type == Payload.Type.Eip7821HermesDelegate } ?: refuse("execute")
        if (authorizations.size > 1 || authorizations.size + 1 != operation.payloads.size) {
            refuse("payloads")
        }
        authorizations.singleOrNull()?.let { readAuthorization(it.meta, chain) }
        val typedData = (execute.meta as? PayloadMeta.Evm.HermesDelegate)?.typedData ?: refuse("execute")
        val (mode, relayed) = readExecute(typedData, chain, sender)
        val requested = calls.map { Call(it.to.lowercase(), it.value.toString(), hexDigits(it.data)) }
        if (relayed == requested) {
            if (mode != relayedMode(EXEC_TYPE_DEFAULT, List(MODE_BYTES - MODE_PAYLOAD_OFFSET) { 0 })) {
                refuse("execute mode")
            }
            return
        }
        val feeToken = feeToken(assetId, chain)
        if (feeToken == null || relayed.size != requested.size + 1 || relayed.drop(1) != requested) {
            refuse("calls")
        }
        val feeFirst = policies(relayed.size) { index ->
            when (index) {
                0 -> REVERTING_POLICY
                else -> INTERRUPTING_POLICY
            }
        }
        val allOrNothing = policies(relayed.size) { REVERTING_POLICY }
        if (mode != relayedMode(EXEC_TYPE_TRY, feeFirst) && mode != relayedMode(EXEC_TYPE_TRY, allOrNothing)) {
            refuse("execute mode")
        }
        readFeeTransfer(relayed.first(), feeToken, fee)
    }

    private fun readAuthorization(meta: PayloadMeta, chain: Chain.Evm) {
        val authorization = meta as? PayloadMeta.Evm.Authorization ?: refuse("authorization")
        if (authorization.chainId != chain.number || !authorization.address.display.equals(HERMES_DELEGATE, ignoreCase = true)) {
            refuse("authorization")
        }
    }

    private fun readExecute(typedData: String, chain: Chain.Evm, sender: String): Pair<List<Int>, List<Call>> {
        val document = try {
            Json.parseToJsonElement(typedData) as? JsonObject
        } catch (e: SerializationException) {
            null
        } ?: refuse("execute document")
        val types = document[TYPES] as? JsonObject ?: refuse("execute types")
        types[EIP712_DOMAIN]?.let { declared ->
            if (fields(declared) != DOMAIN_TYPE) {
                refuse("execute domain type")
            }
        }
        if (types.keys - EIP712_DOMAIN != setOf(EXECUTE, CALL) ||
            fields(types.getValue(EXECUTE)) != EXECUTE_TYPE ||
            fields(types.getValue(CALL)) != CALL_TYPE ||
            document.string(PRIMARY_TYPE) != EXECUTE
        ) {
            refuse("execute types")
        }
        val domain = document[DOMAIN] as? JsonObject ?: refuse("execute domain")
        if (domain.string(NAME) != HERMES_NAME ||
            domain.string(VERSION) != HERMES_VERSION ||
            chainId(domain[CHAIN_ID]) != chain.number ||
            !domain.string(VERIFYING_CONTRACT).equals(sender, ignoreCase = true) ||
            !domain.string(SALT).equals(HEX_PREFIX + hexDigits(HERMES_DELEGATE).padStart(WORD_HEX_LENGTH, '0'), ignoreCase = true)
        ) {
            refuse("execute domain")
        }
        val message = document[MESSAGE] as? JsonObject ?: refuse("execute message")
        val mode = hexDigits(message.string(MODE) ?: refuse("execute mode")).chunked(2).map { it.toInt(HEX_RADIX) }
        val calls = message[CALLS] as? JsonArray ?: refuse("execute calls")
        return mode to calls.map { element ->
            val call = element as? JsonObject ?: refuse("execute calls")
            val target = call.string(TARGET)?.lowercase()?.takeIf { it.length == ADDRESS_HEX_LENGTH + 2 } ?: refuse("execute calls")
            val value = call.string(VALUE)?.takeIf(UINT_DECIMAL::matches)?.let { BigInteger.parseString(it).toString() } ?: refuse("execute calls")
            Call(target, value, hexDigits(call.string(DATA)))
        }
    }

    private fun relayedMode(execType: Int, policies: List<Int>): List<Int> {
        return listOf(CALL_TYPE_BATCH, execType) + List(MODE_SELECTOR_OFFSET - 2) { 0 } + MODE_SELECTOR.map { it.toInt() } + policies
    }

    private fun policies(calls: Int, policyOf: (Int) -> Int): List<Int> {
        val policies = MutableList(MODE_BYTES - MODE_PAYLOAD_OFFSET) { 0 }
        if (calls * POLICY_BITS > policies.size * Byte.SIZE_BITS) {
            refuse("execute mode")
        }
        repeat(calls) { index ->
            val bit = index * POLICY_BITS
            val byte = policies.lastIndex - bit / Byte.SIZE_BITS
            policies[byte] = policies[byte] or (policyOf(index) shl (bit % Byte.SIZE_BITS))
        }
        return policies
    }

    private fun readFeeTransfer(transfer: Call, feeToken: String, fee: BigInteger) {
        val data = transfer.data
        if (transfer.target != feeToken || transfer.value != "0" || data.length != TRANSFER_HEX_LENGTH || !data.startsWith(TRANSFER)) {
            refuse("fee transfer")
        }
        val recipient = data.substring(TRANSFER.length, TRANSFER.length + WORD_HEX_LENGTH)
        val amount = BigInteger.parseString(data.takeLast(WORD_HEX_LENGTH), HEX_RADIX)
        if (!recipient.startsWith("0".repeat(WORD_HEX_LENGTH - ADDRESS_HEX_LENGTH)) || amount > fee) {
            refuse("fee transfer")
        }
    }

    private fun feeToken(assetId: String, chain: Chain.Evm): String? {
        val parts = assetId.lowercase().split('/')
        if (parts.size != TOKEN_ASSET_ID_PARTS || parts[0] != chain.network.type.id || parts[1] != chain.network.mode.id) {
            return null
        }
        return parts[3].takeIf { it.length == ADDRESS_HEX_LENGTH + 2 && it.startsWith(HEX_PREFIX) }
    }

    private fun chainId(element: JsonElement?): Long? {
        val primitive = element as? JsonPrimitive ?: return null
        return if (primitive.isString) {
            primitive.content.takeIf(UINT_DECIMAL::matches)?.toLongOrNull()
        } else {
            primitive.longOrNull
        }
    }

    private fun fields(element: JsonElement): List<Pair<String, String>>? {
        val array = element as? JsonArray ?: return null
        return array.map { field ->
            val entry = field as? JsonObject ?: return null
            val name = entry.string(NAME) ?: return null
            val type = entry.string(TYPE) ?: return null
            name to type
        }
    }

    private fun hexDigits(data: String?): String {
        val digits = data.orEmpty().removePrefix(HEX_PREFIX).lowercase()
        if (!HEX_DIGITS.matches(digits)) {
            refuse("calldata")
        }
        return digits
    }

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun refuse(field: String): Nothing {
        throw IllegalStateException("Relayed operation: unexpected $field")
    }

    private data class Call(val target: String, val value: String, val data: String)
}
