package com.tonapps.perps.funding

import com.ionspin.kotlin.bignum.integer.BigInteger as ChainInteger
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.perps.data.LIGHTER_NIL_ACCOUNT_INDEX
import com.tonapps.perps.security.PerpsSecurityConfig
import com.tonapps.wallet.data.multichain.lighter.PerpsQuoteError
import io.perpsapi.models.ChangePubKey
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.math.BigInteger

private const val HEX_PREFIX = "0x"
private const val CHANGE_PUB_KEY_SELECTOR = "17010c68"
private const val PUBLIC_KEY_BYTES = 40
private const val PUBLIC_KEY_OFFSET = 0x60L
private const val ABI_WORD_HEX_LENGTH = 64
private const val PUBLIC_KEY_PADDED_HEX_LENGTH = ABI_WORD_HEX_LENGTH * 2
private const val HEX_RADIX = 16
private val API_KEY_SLOTS = 0..254
private val PUBLIC_KEY_HEX = Regex("[0-9a-f]{80}")
private const val PREVIEW_PAYLOADS = 2
private const val PAID_FROM_ETHEREUM_PREVIEW_PAYLOADS = 1
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
private const val NONCE = "nonce"
private const val DEADLINE = "deadline"
private const val HERMES_NAME = "Hermes"
private const val HERMES_VERSION = "v1.0.0"
private const val ZERO_VALUE = "0"
private const val SIGNED_BATCH_MODE = "0x0100000000007821000100000000000000000000000000000000000000000000"
private const val SALT_HEX_LENGTH = 64
private const val LONGEST_DEADLINE_SECONDS = 24L * 60L * 60L
private const val UINT256_BITS = 256
private val UINT_DECIMAL = Regex("[0-9]{1,78}")
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

internal class PerpsKeyRegistration private constructor(
    val apiKeyIndex: Int,
    val publicKey: String,
) {

    val calldata: String
        get() = HEX_PREFIX + CHANGE_PUB_KEY_SELECTOR +
            abiWord(BigInteger.valueOf(LIGHTER_NIL_ACCOUNT_INDEX)) +
            abiWord(BigInteger.valueOf(apiKeyIndex.toLong())) +
            abiWord(BigInteger.valueOf(PUBLIC_KEY_OFFSET)) +
            abiWord(BigInteger.valueOf(PUBLIC_KEY_BYTES.toLong())) +
            publicKey.removePrefix(HEX_PREFIX).padEnd(PUBLIC_KEY_PADDED_HEX_LENGTH, '0')

    fun request(): ChangePubKey = ChangePubKey(
        accountIndex = LIGHTER_NIL_ACCOUNT_INDEX,
        apiKeyIndex = apiKeyIndex,
        pubKey = publicKey,
    )

    companion object {

        fun of(apiKeyIndex: Int, publicKey: String): PerpsKeyRegistration? {
            val hex = publicKey.lowercase().removePrefix(HEX_PREFIX)
            if (apiKeyIndex !in API_KEY_SLOTS || !PUBLIC_KEY_HEX.matches(hex)) {
                return null
            }
            return PerpsKeyRegistration(apiKeyIndex, HEX_PREFIX + hex)
        }
    }
}

internal class PerpsKeyRegistrationPreview(
    val operationId: String,
    val payloads: List<Payload>,
)

internal object PerpsKeyRegistrationReader {

    fun read(
        preview: Operation,
        owner: String,
        source: PerpsDepositSource,
        registration: PerpsKeyRegistration,
        security: PerpsSecurityConfig,
        nowSeconds: Long,
    ): PerpsKeyRegistrationPreview {
        if (!security.delegatesOnEthereum) {
            refuse("hermes_delegate.chains")
        }
        val paidFromEthereum = source.isOnEthereum
        val paidByOwner = source.account.data.displayAddress.equals(owner, ignoreCase = true)
        if (paidFromEthereum && (source.isBestValue || !paidByOwner)) {
            refuse("ethereum source")
        }
        val payloads = preview.payloads
        val expectedPayloads = if (paidFromEthereum) {
            PAID_FROM_ETHEREUM_PREVIEW_PAYLOADS
        } else {
            PREVIEW_PAYLOADS
        }
        if (payloads.size != expectedPayloads || payloads.any { it.chain != Chain.Ethereum.Mainnet || it.version != Payload.VERSION }) {
            refuse("payloads")
        }
        val execute = payloads.singleOrNull { it.type == Payload.Type.Eip7821HermesDelegate } ?: refuse("payloads")
        if (!paidFromEthereum) {
            val authorization = payloads.singleOrNull { it.type == Payload.Type.Eip7702Authorization } ?: refuse("payloads")
            readAuthorization(authorization.meta, security)
        }
        val document = (execute.meta as? PayloadMeta.Evm.HermesDelegate)?.typedData ?: refuse("execute")
        val terms = readExecute(document, owner, registration, security, nowSeconds)
        val signable = PayloadMeta.Evm.HermesDelegate(executeDocument(owner, registration, security, terms).toString())
        return PerpsKeyRegistrationPreview(
            operationId = preview.id,
            payloads = payloads.map { payload ->
                if (payload === execute) {
                    execute.copy(meta = signable)
                } else {
                    payload
                }
            },
        )
    }

    fun readPayment(payment: Operation, security: PerpsSecurityConfig?) {
        payment.payloads.forEach { payload ->
            when (val meta = payload.meta) {
                is PayloadMeta.Evm.Authorization, is PayloadMeta.Evm.HermesDelegate -> refuse("payment")
                is PayloadMeta.Evm.Main -> meta.authorization?.let { authorization ->
                    readPaymentDelegation(payload.chain, authorization, security ?: refuse("payment authorization"))
                }
                else -> Unit
            }
        }
    }

    fun readPaymentDelegation(chain: Chain, authorization: PayloadMeta.Evm.Authorization, security: PerpsSecurityConfig) {
        if (chain.network.type.id !in security.hermesDelegateChains ||
            authorization.chainId != (chain as? Chain.Evm)?.number ||
            !authorization.address.display.equals(security.hermesDelegate, ignoreCase = true) ||
            authorization.nonce < ChainInteger.ONE
        ) {
            refuse("payment authorization")
        }
    }

    private fun readAuthorization(meta: PayloadMeta, security: PerpsSecurityConfig) {
        val authorization = meta as? PayloadMeta.Evm.Authorization ?: refuse("authorization")
        if (authorization.chainId != Chain.Ethereum.Mainnet.number ||
            !authorization.address.display.equals(security.hermesDelegate, ignoreCase = true) ||
            authorization.nonce < ChainInteger.ZERO
        ) {
            refuse("authorization")
        }
    }

    private fun readExecute(
        typedData: String,
        owner: String,
        registration: PerpsKeyRegistration,
        security: PerpsSecurityConfig,
        nowSeconds: Long,
    ): ExecuteTerms {
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
            (domain[CHAIN_ID] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull != Chain.Ethereum.Mainnet.number ||
            !domain.string(VERIFYING_CONTRACT).equals(owner, ignoreCase = true) ||
            !domain.string(SALT).equals(salt(security), ignoreCase = true)
        ) {
            refuse("execute domain")
        }

        val message = document[MESSAGE] as? JsonObject ?: refuse("execute message")
        val call = (message[CALLS] as? JsonArray)?.singleOrNull() as? JsonObject
        if (!message.string(MODE).equals(SIGNED_BATCH_MODE, ignoreCase = true) ||
            call == null ||
            !call.string(TARGET).equals(security.changePubKeyContract, ignoreCase = true) ||
            call.string(VALUE) != ZERO_VALUE ||
            !call.string(DATA).equals(registration.calldata, ignoreCase = true)
        ) {
            refuse("execute call")
        }

        val nonce = message.string(NONCE)?.takeIf(UINT_DECIMAL::matches)?.let(::BigInteger)?.takeIf { it.bitLength() <= UINT256_BITS }
        val deadline = message.string(DEADLINE)?.takeIf(UINT_DECIMAL::matches)?.toLongOrNull()
        if (nonce == null || deadline == null || deadline <= nowSeconds || deadline > nowSeconds + LONGEST_DEADLINE_SECONDS) {
            refuse("execute nonce or deadline")
        }
        return ExecuteTerms(nonce = nonce, deadline = deadline)
    }

    private fun executeDocument(
        owner: String,
        registration: PerpsKeyRegistration,
        security: PerpsSecurityConfig,
        terms: ExecuteTerms,
    ): JsonObject = buildJsonObject {
        putJsonObject(TYPES) {
            put(EIP712_DOMAIN, typeArray(DOMAIN_TYPE))
            put(EXECUTE, typeArray(EXECUTE_TYPE))
            put(CALL, typeArray(CALL_TYPE))
        }
        put(PRIMARY_TYPE, EXECUTE)
        putJsonObject(DOMAIN) {
            put(NAME, HERMES_NAME)
            put(VERSION, HERMES_VERSION)
            put(CHAIN_ID, Chain.Ethereum.Mainnet.number)
            put(VERIFYING_CONTRACT, owner)
            put(SALT, salt(security))
        }
        putJsonObject(MESSAGE) {
            put(MODE, SIGNED_BATCH_MODE)
            putJsonArray(CALLS) {
                addJsonObject {
                    put(TARGET, security.changePubKeyContract)
                    put(VALUE, ZERO_VALUE)
                    put(DATA, registration.calldata)
                }
            }
            put(NONCE, terms.nonce.toString())
            put(DEADLINE, terms.deadline.toString())
        }
    }

    private fun salt(security: PerpsSecurityConfig): String {
        return HEX_PREFIX + security.hermesDelegate.removePrefix(HEX_PREFIX).padStart(SALT_HEX_LENGTH, '0')
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

    private fun typeArray(fields: List<Pair<String, String>>): JsonArray = buildJsonArray {
        fields.forEach { (name, type) ->
            addJsonObject {
                put(NAME, name)
                put(TYPE, type)
            }
        }
    }

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun refuse(field: String): Nothing {
        throw PerpsDepositException.QuoteRejected(PerpsQuoteError.MalformedKeyRegistration(field))
    }

    private class ExecuteTerms(val nonce: BigInteger, val deadline: Long)
}

private fun abiWord(value: BigInteger): String = value.toString(HEX_RADIX).padStart(ABI_WORD_HEX_LENGTH, '0')
