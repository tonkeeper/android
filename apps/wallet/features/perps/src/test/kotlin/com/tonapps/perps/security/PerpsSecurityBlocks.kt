package com.tonapps.perps.security

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom
import java.util.Base64

private const val ISSUED_AT = 1_794_223_996L

object PerpsSecurityBlocks {

    const val HERMES_DELEGATE = "0x24b576bc271823bf9c24be627b4363b9b00191e9"
    const val LIGHTER_GATEWAY = "0x3b4d794a66304f130a4db8f2551b0070dfcf5ca7"
    const val LIGHTER_CHANGE_PUB_KEY = "0x183ef7195d443a3da7ec1d7003baca1ff490757d"
    const val RELAY_EVM_DEPOSITORY = "0x4cd00e387622c35bddb9b4c962c136462338bc31"
    const val RELAY_LIGHTER_ACCOUNT = 731_033L
    const val RELAY_TON_DEPOSITORY = "0:ab746b034ea036b7ac5147837a57827bd3917bc3f5506d47479e87ec87ef2554"
    const val RELAY_TON_DEPOSITORY_FRIENDLY = "EQCrdGsDTqA2t6xRR4N6V4J705F7w_VQbUdHnofsh-8lVIPs"
    const val RELAY_BTC_DEPOSITORY = "bc1qzmtn0q92ayejt2hpffvlktcpmyy7vvsd06sefu"
    const val RELAY_ERC20_ROUTER = "0x0729e2a4716d9f7dd17858d5d504426d228697f7"
    const val RELAY_APPROVAL_PROXY = "0x5567954cd026cebfe486062cb75848e0f1c12e58"
    const val RELAY_RECEIVER = "0xa5f565650890fba1824ee0f21ebbbf660a179934"
    const val KEY_VERSION = 1

    fun newKey(): Ed25519PrivateKeyParameters = Ed25519PrivateKeyParameters(SecureRandom())

    fun trusting(key: Ed25519PrivateKeyParameters): Map<Int, ByteArray> {
        return mapOf(KEY_VERSION to key.generatePublicKey().encoded)
    }

    fun payload(
        version: Long = 1,
        network: String = "mainnet",
        delegate: String = HERMES_DELEGATE,
        delegateChains: List<String> = listOf("eth", "arb"),
        changePubKey: String? = LIGHTER_CHANGE_PUB_KEY,
        changePubKeyChain: String = "eth",
        relayDepositories: List<Pair<String, JsonPrimitive>>? = listOf(
            "eth" to JsonPrimitive(RELAY_EVM_DEPOSITORY),
            "lighter" to JsonPrimitive(RELAY_LIGHTER_ACCOUNT.toString()),
            "ton" to JsonPrimitive(RELAY_TON_DEPOSITORY),
            "btc" to JsonPrimitive(RELAY_BTC_DEPOSITORY),
        ),
        markets: List<JsonObject>? = null,
        relayRouters: List<JsonObject>? = null,
    ): JsonObject = buildJsonObject {
        put("version", version)
        put("issued_at", ISSUED_AT)
        put("network", network)
        putJsonObject("contracts") {
            putJsonObject("lighter_gateway") {
                put("chain", "eth")
                put("address", LIGHTER_GATEWAY)
            }
            if (changePubKey != null) {
                putJsonObject("lighter_change_pub_key") {
                    put("chain", changePubKeyChain)
                    put("address", changePubKey)
                }
            }
            putJsonObject("hermes_delegate") {
                put("address", delegate)
                putJsonArray("chains") { delegateChains.forEach { add(JsonPrimitive(it)) } }
            }
        }
        if (relayDepositories != null) {
            putJsonArray("relay_depositories") {
                relayDepositories.forEach { (chain, address) ->
                    add(
                        buildJsonObject {
                            put("chain", chain)
                            put("address", address)
                        },
                    )
                }
            }
        }
        if (markets != null) {
            putJsonArray("markets") { markets.forEach { add(it) } }
        }
        if (relayRouters != null) {
            putJsonArray("relay_routers") { relayRouters.forEach { add(it) } }
        }
    }

    fun relayRouters(vararg chains: String): List<JsonObject> = chains.flatMap { chain ->
        listOf(
            relayRouter(chain, RELAY_ERC20_ROUTER, "erc20_router"),
            relayRouter(chain, RELAY_APPROVAL_PROXY, "approval_proxy"),
            relayRouter(chain, RELAY_RECEIVER, "relay_receiver"),
        )
    }

    fun relayRouter(chain: String, address: String, type: String): JsonObject = buildJsonObject {
        put("chain", chain)
        put("address", address)
        put("type", type)
    }

    fun market(
        marketId: Int,
        symbol: String = "LIT",
        priceDecimals: Int = 5,
        sizeDecimals: Int = 1,
        quoteDecimals: Int = priceDecimals + sizeDecimals,
        minBaseAmount: String = "0.1",
        minQuoteAmount: String = "1.000000",
        maxLeverage: Int = 10,
    ): JsonObject = buildJsonObject {
        put("market_id", marketId)
        put("symbol", symbol)
        put("size_decimals", sizeDecimals)
        put("price_decimals", priceDecimals)
        put("quote_decimals", quoteDecimals)
        put("min_base_amount", minBaseAmount)
        put("min_quote_amount", minQuoteAmount)
        put("max_leverage", maxLeverage)
    }

    fun signed(
        key: Ed25519PrivateKeyParameters,
        payload: JsonObject = payload(),
        keyVersion: Int = KEY_VERSION,
        servedPayload: ByteArray? = null,
    ): String {
        val bytes = payload.toString().toByteArray()
        val signer = Ed25519Signer()
        signer.init(true, key)
        signer.update(bytes, 0, bytes.size)
        return buildJsonObject {
            put("payload", Base64.getEncoder().encodeToString(servedPayload ?: bytes))
            put("signature", Base64.getEncoder().encodeToString(signer.generateSignature()))
            put("key_version", keyVersion)
        }.toString()
    }
}
