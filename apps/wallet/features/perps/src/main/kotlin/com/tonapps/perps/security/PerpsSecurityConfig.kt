package com.tonapps.perps.security

import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.log.L
import com.tonapps.perps.data.toWireDecimalOrNull
import com.tonapps.perps.trade.PerpsProductRules
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.ton.block.AddrStd
import java.math.BigDecimal
import java.util.Base64

class PerpsSecurityConfig(
    val version: Long,
    val hermesDelegate: String,
    val hermesDelegateChains: Set<String>,
    val changePubKeyContract: String,
    val lighterRelayAccounts: Set<Long>,
    val tonRelayDepositories: Set<String>,
    val lighterGateway: String?,
    val evmRelayDepositories: Map<String, Set<String>>,
    val evmRelayRouters: Map<String, Map<String, PerpsRelayRouter>>,
    val tronRelayDepositories: Set<String>,
    val btcRelayDepositories: Set<String>,
    val markets: Map<Int, PerpsSignedMarket>,
) {

    val delegatesOnEthereum: Boolean
        get() = ETHEREUM in hermesDelegateChains

    fun marketIndex(symbol: String): Int? {
        return markets.entries.firstOrNull { (_, market) -> market.symbol.equals(symbol, ignoreCase = true) }?.key
    }

    internal companion object {
        const val ETHEREUM = "eth"
    }
}

enum class PerpsRelayRouter(val signedType: String) {
    Erc20Router("erc20_router"),
    ApprovalProxy("approval_proxy"),
    RelayReceiver("relay_receiver"),
}

class PerpsSignedMarket(
    val marketId: Int,
    val symbol: String,
    val priceDecimals: Int,
    val sizeDecimals: Int,
    val quoteDecimals: Int,
    val minBaseAmount: BigDecimal,
    val minQuoteAmount: BigDecimal,
    val maxLeverage: Int,
)

private const val PAYLOAD = "payload"
private const val SIGNATURE = "signature"
private const val KEY_VERSION = "key_version"
private const val VERSION = "version"
private const val NETWORK = "network"
private const val MAINNET = "mainnet"
private const val CONTRACTS = "contracts"
private const val HERMES_DELEGATE = "hermes_delegate"
private const val LIGHTER_CHANGE_PUB_KEY = "lighter_change_pub_key"
private const val LIGHTER_GATEWAY = "lighter_gateway"
private const val ADDRESS = "address"
private const val CHAINS = "chains"
private const val CHAIN = "chain"
private const val RELAY_DEPOSITORIES = "relay_depositories"
private const val RELAY_ROUTERS = "relay_routers"
private const val TYPE = "type"
private const val LIGHTER = "lighter"
private const val TON = "ton"
private const val TRON = "tron"
private const val BTC = "btc"
private const val MARKETS = "markets"
private const val MARKET_ID = "market_id"
private const val SYMBOL = "symbol"
private const val PRICE_DECIMALS = "price_decimals"
private const val SIZE_DECIMALS = "size_decimals"
private const val QUOTE_DECIMALS = "quote_decimals"
private const val MIN_BASE_AMOUNT = "min_base_amount"
private const val MIN_QUOTE_AMOUNT = "min_quote_amount"
private const val MAX_LEVERAGE = "max_leverage"
private const val MAX_MARKET_DECIMALS = 18
private val MARKET_AMOUNT = Regex("[0-9]+(\\.[0-9]+)?")
private val EVM_ADDRESS = Regex("0x[0-9a-fA-F]{40}")
private val LIGHTER_ACCOUNT_INDEX = Regex("[1-9][0-9]*")
private val TRON_ADDRESS = Regex("T[1-9A-HJ-NP-Za-km-z]{33}")
private val BTC_BECH32_ADDRESS = Regex("bc1[02-9ac-hj-np-z]{8,87}")
private val BTC_BASE58_ADDRESS = Regex("[13][1-9A-HJ-NP-Za-km-z]{25,34}")

internal object PerpsSecurityConfigReader {

    fun read(block: String, trustedKeys: Map<Int, ByteArray>): PerpsSecurityConfig? {
        val envelope = parseObject(block.toByteArray(Charsets.UTF_8)) ?: return refuse("envelope is not a JSON object")
        val keyVersion = envelope.primitive(KEY_VERSION)?.takeUnless { it.isString }?.intOrNull
            ?: return refuse("no key_version")
        val key = trustedKeys[keyVersion] ?: return refuse("unknown key_version $keyVersion")
        val payload = envelope.primitive(PAYLOAD)?.takeIf { it.isString }?.content?.decodeBase64()
            ?: return refuse("payload is not base64")
        val signature = envelope.primitive(SIGNATURE)?.takeIf { it.isString }?.content?.decodeBase64()
            ?.takeIf { it.size == Ed25519PublicKeyParameters.KEY_SIZE * 2 }
            ?: return refuse("signature is not base64 of 64 bytes")
        if (!verify(key, payload, signature)) {
            return refuse("signature does not match key_version $keyVersion")
        }
        return parse(payload)
    }

    private fun verify(key: ByteArray, payload: ByteArray, signature: ByteArray): Boolean {
        if (key.size != Ed25519PublicKeyParameters.KEY_SIZE) {
            return false
        }
        val signer = Ed25519Signer()
        signer.init(false, Ed25519PublicKeyParameters(key, 0))
        signer.update(payload, 0, payload.size)
        return signer.verifySignature(signature)
    }

    private fun parse(payload: ByteArray): PerpsSecurityConfig? {
        val config = parseObject(payload) ?: return refuse("payload is not a JSON object")
        val version = config.primitive(VERSION)?.takeUnless { it.isString }?.longOrNull?.takeIf { it >= 1 }
            ?: return refuse("no version")
        if (config.primitive(NETWORK)?.takeIf { it.isString }?.content != MAINNET) {
            return refuse("not a mainnet config")
        }
        val contracts = config[CONTRACTS] as? JsonObject ?: return refuse("no contracts")
        val delegate = contracts[HERMES_DELEGATE] as? JsonObject ?: return refuse("no hermes_delegate")
        val delegateAddress = delegate.evmAddress(ADDRESS) ?: return refuse("hermes_delegate.address")
        val delegateChains = (delegate[CHAINS] as? JsonArray)
            ?.map { (it as? JsonPrimitive)?.takeIf { chain -> chain.isString }?.content ?: return refuse("hermes_delegate.chains") }
            ?: return refuse("hermes_delegate.chains")
        val changePubKeyAddress = contracts.ethereumContract(LIGHTER_CHANGE_PUB_KEY)
            ?: return refuse(LIGHTER_CHANGE_PUB_KEY)
        return PerpsSecurityConfig(
            version = version,
            hermesDelegate = delegateAddress,
            hermesDelegateChains = delegateChains.toSet(),
            changePubKeyContract = changePubKeyAddress,
            lighterRelayAccounts = lighterRelayAccounts(config),
            tonRelayDepositories = tonRelayDepositories(config),
            lighterGateway = contracts.ethereumContract(LIGHTER_GATEWAY),
            evmRelayDepositories = evmRelayDepositories(config),
            evmRelayRouters = evmRelayRouters(config),
            tronRelayDepositories = tronRelayDepositories(config),
            btcRelayDepositories = btcRelayDepositories(config),
            markets = markets(config),
        )
    }

    private fun markets(config: JsonObject): Map<Int, PerpsSignedMarket> {
        val entries = (config[MARKETS] as? JsonArray).orEmpty()
        if (entries.isEmpty()) {
            L.w("perps security config lists no markets")
        }
        val markets = entries.mapNotNull { entry ->
            (entry as? JsonObject)?.let(::market) ?: run {
                L.w("perps security config: unreadable market entry $entry")
                null
            }
        }
        val byMarketId = markets.groupBy { it.marketId }
        byMarketId.filterValues { it.size > 1 }.keys.forEach { marketId ->
            L.w("perps security config lists market $marketId more than once")
        }
        return byMarketId.filterValues { it.size == 1 }.mapValues { (_, listed) -> listed.single() }
    }

    private fun market(entry: JsonObject): PerpsSignedMarket? {
        return PerpsSignedMarket(
            marketId = entry.number(MARKET_ID)?.takeIf { it in 0..Short.MAX_VALUE } ?: return null,
            symbol = entry.primitive(SYMBOL)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() } ?: return null,
            priceDecimals = entry.decimals(PRICE_DECIMALS) ?: return null,
            sizeDecimals = entry.decimals(SIZE_DECIMALS) ?: return null,
            quoteDecimals = entry.decimals(QUOTE_DECIMALS) ?: return null,
            minBaseAmount = entry.amount(MIN_BASE_AMOUNT) ?: return null,
            minQuoteAmount = entry.amount(MIN_QUOTE_AMOUNT) ?: return null,
            maxLeverage = entry.number(MAX_LEVERAGE)?.takeIf { it in 0..PerpsProductRules.IMF_TICKS } ?: return null,
        )
    }

    private fun JsonObject.number(key: String): Int? = primitive(key)?.takeUnless { it.isString }?.intOrNull

    private fun JsonObject.decimals(key: String): Int? = number(key)?.takeIf { it in 0..MAX_MARKET_DECIMALS }

    private fun JsonObject.amount(key: String): BigDecimal? {
        return primitive(key)?.takeIf { it.isString }?.content?.takeIf(MARKET_AMOUNT::matches)?.toWireDecimalOrNull()
    }

    private fun evmRelayDepositories(config: JsonObject): Map<String, Set<String>> {
        return entries(config)
            .mapNotNull { entry ->
                val chain = entry.primitive(CHAIN)?.takeIf { it.isString }?.content ?: return@mapNotNull null
                val address = entry.evmAddress(ADDRESS) ?: return@mapNotNull null
                chain to address
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .mapValues { (_, addresses) -> addresses.toSet() }
    }

    private fun evmRelayRouters(config: JsonObject): Map<String, Map<String, PerpsRelayRouter>> {
        val routers = (config[RELAY_ROUTERS] as? JsonArray).orEmpty().mapNotNull { entry ->
            (entry as? JsonObject)?.let(::relayRouter) ?: run {
                L.w("perps security config: unreadable Relay router entry $entry")
                null
            }
        }
        if (routers.isEmpty()) {
            L.w("perps security config names no Relay router")
        }
        return routers
            .groupBy { it.chain }
            .mapValues { (chain, listed) ->
                listed.groupBy(keySelector = { it.address }, valueTransform = { it.router })
                    .mapValues { (_, roles) -> roles.toSet() }
                    .mapNotNull { (address, roles) ->
                        roles.singleOrNull()?.let { address to it } ?: run {
                            L.w("perps security config lists Relay router $address on $chain as $roles")
                            null
                        }
                    }
                    .toMap()
            }
            .filterValues { it.isNotEmpty() }
    }

    private fun relayRouter(entry: JsonObject): SignedRelayRouter? {
        val type = entry.primitive(TYPE)?.takeIf { it.isString }?.content
        return SignedRelayRouter(
            chain = entry.primitive(CHAIN)?.takeIf { it.isString }?.content ?: return null,
            address = entry.evmAddress(ADDRESS) ?: return null,
            router = PerpsRelayRouter.entries.firstOrNull { it.signedType == type } ?: return null,
        )
    }

    private fun tronRelayDepositories(config: JsonObject): Set<String> {
        return relayDepositories(config, TRON)
            .mapNotNull { it.primitive(ADDRESS)?.takeIf { address -> address.isString }?.content?.takeIf(TRON_ADDRESS::matches) }
            .toSet()
    }

    private fun lighterRelayAccounts(config: JsonObject): Set<Long> {
        val accounts = relayDepositories(config, LIGHTER)
            .mapNotNull { it.lighterAccountIndex(ADDRESS) }
            .toSet()
        if (accounts.isEmpty()) {
            L.w("perps security config names no Relay account on Lighter")
        }
        return accounts
    }

    private fun tonRelayDepositories(config: JsonObject): Set<String> {
        val depositories = relayDepositories(config, TON)
            .mapNotNull { it.primitive(ADDRESS)?.takeIf { address -> address.isString }?.content?.let(::tonAccountId) }
            .toSet()
        if (depositories.isEmpty()) {
            L.w("perps security config names no Relay depository on TON")
        }
        return depositories
    }

    private fun btcRelayDepositories(config: JsonObject): Set<String> {
        val depositories = relayDepositories(config, BTC)
            .mapNotNull { it.primitive(ADDRESS)?.takeIf { address -> address.isString }?.content?.let(::btcAddress) }
            .toSet()
        if (depositories.isEmpty()) {
            L.w("perps security config names no Relay depository on Bitcoin")
        }
        return depositories
    }

    private fun relayDepositories(config: JsonObject, chain: String): List<JsonObject> {
        return entries(config)
            .filter { it.primitive(CHAIN)?.takeIf { entry -> entry.isString }?.content == chain }
    }

    private fun entries(config: JsonObject): List<JsonObject> {
        return (config[RELAY_DEPOSITORIES] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    }

    private fun parseObject(bytes: ByteArray): JsonObject? {
        return try {
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject
        } catch (e: SerializationException) {
            null
        }
    }

    private fun String.decodeBase64(): ByteArray? {
        return try {
            Base64.getDecoder().decode(this)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? = get(key) as? JsonPrimitive

    private fun JsonObject.ethereumContract(key: String): String? {
        val contract = get(key) as? JsonObject ?: return null
        if (contract.primitive(CHAIN)?.takeIf { it.isString }?.content != PerpsSecurityConfig.ETHEREUM) {
            return null
        }
        return contract.evmAddress(ADDRESS)
    }

    private fun JsonObject.lighterAccountIndex(key: String): Long? {
        return primitive(key)?.takeIf { it.isString }?.content?.takeIf(LIGHTER_ACCOUNT_INDEX::matches)?.toLongOrNull()
    }

    private fun JsonObject.evmAddress(key: String): String? {
        return primitive(key)?.takeIf { it.isString }?.contentOrNull?.takeIf(EVM_ADDRESS::matches)?.lowercase()
    }

    private fun refuse(reason: String): PerpsSecurityConfig? {
        L.w("perps security config refused: $reason")
        return null
    }

    private class SignedRelayRouter(val chain: String, val address: String, val router: PerpsRelayRouter)
}

internal fun btcAddress(address: String): String? {
    val bech32 = address.lowercase()
    return when {
        BTC_BASE58_ADDRESS.matches(address) -> address
        address != bech32 && address != address.uppercase() -> null
        BTC_BECH32_ADDRESS.matches(bech32) -> bech32
        else -> null
    }
}

internal fun tonAccountId(address: String): String? {
    return try {
        AddrStd(address).toAccountId()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
