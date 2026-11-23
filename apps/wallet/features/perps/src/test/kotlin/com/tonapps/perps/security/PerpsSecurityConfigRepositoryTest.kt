package com.tonapps.perps.security

import com.tonapps.wallet.testing.BaseUnitTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PerpsSecurityConfigRepositoryTest : BaseUnitTest() {

    private val key = PerpsSecurityBlocks.newKey()
    private val store = MemoryStore()
    private var served: String? = null
    private val repository = PerpsSecurityConfigRepository(
        servedBlock = { served },
        store = store,
        trustedKeys = PerpsSecurityBlocks.trusting(key),
    )

    @Test
    fun theBlockTheBackendServesTodayVerifiesWithTheBuiltInKey() = runTest {
        val production = PerpsSecurityConfigRepository(servedBlock = { PRODUCTION_BLOCK_V2 }, store = store)

        val config = checkNotNull(production.verified())

        assertEquals(2L, config.version)
        assertEquals(PerpsSecurityBlocks.HERMES_DELEGATE, config.hermesDelegate)
        assertEquals(PerpsSecurityBlocks.LIGHTER_CHANGE_PUB_KEY, config.changePubKeyContract)
        assertEquals(setOf("eth", "bsc", "arb", "base", "polygon", "robinhood"), config.hermesDelegateChains)
        assertEquals(setOf(PerpsSecurityBlocks.RELAY_LIGHTER_ACCOUNT), config.lighterRelayAccounts)
        assertEquals(setOf(PerpsSecurityBlocks.RELAY_TON_DEPOSITORY), config.tonRelayDepositories)
        assertEquals(PerpsSecurityBlocks.LIGHTER_GATEWAY, config.lighterGateway)
        val evmChains = setOf("arb", "avax", "base", "bsc", "eth", "polygon", "robinhood")
        assertEquals(evmChains.associateWith { setOf(PerpsSecurityBlocks.RELAY_EVM_DEPOSITORY) }, config.evmRelayDepositories)
        assertEquals(setOf(TRON_DEPOSITORY), config.tronRelayDepositories)
        assertEquals(setOf(PerpsSecurityBlocks.RELAY_BTC_DEPOSITORY), config.btcRelayDepositories)
        assertTrue(config.evmRelayRouters.isEmpty())
        assertEquals(PRODUCTION_BLOCK_V2, store.block)
    }

    @Test
    fun aMarketIsFoundBySignedSymbolIgnoringCase() = runTest {
        served = PerpsSecurityBlocks.signed(
            key,
            PerpsSecurityBlocks.payload(
                markets = listOf(
                    PerpsSecurityBlocks.market(marketId = 193, symbol = "ANTHROPIC"),
                    PerpsSecurityBlocks.market(marketId = 10902, symbol = "ANT"),
                ),
            ),
        )

        val config = checkNotNull(repository.verified())

        assertEquals(10902, config.marketIndex("ant"))
        assertEquals(193, config.marketIndex("Anthropic"))
        assertNull(config.marketIndex("GRAM"))
    }

    @Test
    fun onlyABitcoinAddressListedForBtcIsABitcoinDepositoryInOneCase() = runTest {
        served = PerpsSecurityBlocks.signed(
            key,
            PerpsSecurityBlocks.payload(
                relayDepositories = listOf(
                    "eth" to JsonPrimitive(OTHER_BTC_ADDRESS),
                    "BTC" to JsonPrimitive(OTHER_BTC_ADDRESS),
                    "btc" to JsonPrimitive(42),
                    "btc" to JsonPrimitive("relay"),
                    "btc" to JsonPrimitive(""),
                    "btc" to JsonPrimitive("bc1qzmtn0q92ayejt2hpffvlktcpmyy7vvsd06sEFU"),
                    "btc" to JsonPrimitive(PerpsSecurityBlocks.RELAY_BTC_DEPOSITORY.uppercase()),
                    "btc" to JsonPrimitive(LEGACY_BTC_ADDRESS),
                ),
            ),
        )

        val config = checkNotNull(repository.verified())

        assertEquals(setOf(PerpsSecurityBlocks.RELAY_BTC_DEPOSITORY, LEGACY_BTC_ADDRESS), config.btcRelayDepositories)
        assertTrue(config.tonRelayDepositories.isEmpty())
    }

    @Test
    fun onlyEvmAddressesAreEvmDepositoriesAndOnlyTronAddressesTronOnes() = runTest {
        served = PerpsSecurityBlocks.signed(
            key,
            JsonObject(
                PerpsSecurityBlocks.payload(
                    relayDepositories = listOf(
                        "bsc" to JsonPrimitive(PerpsSecurityBlocks.RELAY_EVM_DEPOSITORY.uppercase().replace("0X", "0x")),
                        "bsc" to JsonPrimitive("0x1234"),
                        "base" to JsonPrimitive(TRON_DEPOSITORY),
                        "tron" to JsonPrimitive(PerpsSecurityBlocks.RELAY_EVM_DEPOSITORY),
                        "tron" to JsonPrimitive("T0" + TRON_DEPOSITORY.drop(2)),
                        "tron" to JsonPrimitive(TRON_DEPOSITORY),
                    ),
                ),
            ),
        )

        val config = checkNotNull(repository.verified())

        assertEquals(setOf(PerpsSecurityBlocks.RELAY_EVM_DEPOSITORY), config.evmRelayDepositories["bsc"])
        assertNull(config.evmRelayDepositories["base"])
        assertEquals(setOf(TRON_DEPOSITORY), config.tronRelayDepositories)
    }

    @Test
    fun aBlockWithoutTheGatewayIsStillAConfigWithoutOne() = runTest {
        val payload = PerpsSecurityBlocks.payload()
        val contracts = JsonObject(payload.getValue("contracts").jsonObject - "lighter_gateway")
        served = PerpsSecurityBlocks.signed(key, JsonObject(payload + ("contracts" to contracts)))

        val config = checkNotNull(repository.verified())

        assertNull(config.lighterGateway)
    }

    @Test
    fun onlyAnAddressListedForTonIsATonDepositoryAndItIsKeptRaw() = runTest {
        served = PerpsSecurityBlocks.signed(
            key,
            PerpsSecurityBlocks.payload(
                relayDepositories = listOf(
                    "eth" to JsonPrimitive(OTHER_TON_ADDRESS),
                    "TON" to JsonPrimitive(OTHER_TON_ADDRESS),
                    "ton" to JsonPrimitive(42),
                    "ton" to JsonPrimitive("relay"),
                    "ton" to JsonPrimitive(""),
                    "ton" to JsonPrimitive(PerpsSecurityBlocks.RELAY_TON_DEPOSITORY_FRIENDLY),
                ),
            ),
        )

        val config = checkNotNull(repository.verified())

        assertEquals(setOf(PerpsSecurityBlocks.RELAY_TON_DEPOSITORY), config.tonRelayDepositories)
    }

    @Test
    fun aBlockWithoutATonDepositoryIsStillAConfigWithItsLighterAccount() = runTest {
        served = PerpsSecurityBlocks.signed(
            key,
            PerpsSecurityBlocks.payload(relayDepositories = listOf("lighter" to JsonPrimitive(PerpsSecurityBlocks.RELAY_LIGHTER_ACCOUNT.toString()))),
        )

        val config = checkNotNull(repository.verified())

        assertTrue(config.tonRelayDepositories.isEmpty())
        assertEquals(setOf(PerpsSecurityBlocks.RELAY_LIGHTER_ACCOUNT), config.lighterRelayAccounts)
    }

    @Test
    fun aKeptBlockWithoutARelayAccountIsReplacedByAServedOneOfTheSameVersion() = runTest {
        served = PerpsSecurityBlocks.signed(key, PerpsSecurityBlocks.payload(version = 2, relayDepositories = null))
        assertTrue(checkNotNull(repository.verified()).lighterRelayAccounts.isEmpty())

        served = PerpsSecurityBlocks.signed(key, PerpsSecurityBlocks.payload(version = 2))
        val config = checkNotNull(repository.verified())

        assertEquals(setOf(PerpsSecurityBlocks.RELAY_LIGHTER_ACCOUNT), config.lighterRelayAccounts)
        assertEquals(served, store.block)
    }

    @Test
    fun aBlockWithoutRelayDepositoriesIsStillAConfig() = runTest {
        served = PerpsSecurityBlocks.signed(key, PerpsSecurityBlocks.payload(relayDepositories = null))

        val config = checkNotNull(repository.verified())

        assertEquals(PerpsSecurityBlocks.HERMES_DELEGATE, config.hermesDelegate)
        assertEquals(PerpsSecurityBlocks.LIGHTER_CHANGE_PUB_KEY, config.changePubKeyContract)
        assertTrue(config.lighterRelayAccounts.isEmpty())
        assertEquals(served, store.block)
    }

    @Test
    fun onlyAnAccountIndexListedForLighterIsARelayAccount() = runTest {
        served = PerpsSecurityBlocks.signed(
            key,
            PerpsSecurityBlocks.payload(
                relayDepositories = listOf(
                    "eth" to JsonPrimitive(PerpsSecurityBlocks.RELAY_EVM_DEPOSITORY),
                    "eth" to JsonPrimitive("42"),
                    "Lighter" to JsonPrimitive("43"),
                    "lighter" to JsonPrimitive(44),
                    "lighter" to JsonPrimitive("045"),
                    "lighter" to JsonPrimitive("-46"),
                    "lighter" to JsonPrimitive("47a"),
                    "lighter" to JsonPrimitive(""),
                    "lighter" to JsonPrimitive("99999999999999999999"),
                    "lighter" to JsonPrimitive(PerpsSecurityBlocks.RELAY_LIGHTER_ACCOUNT.toString()),
                ),
            ),
        )

        val config = checkNotNull(repository.verified())

        assertEquals(setOf(PerpsSecurityBlocks.RELAY_LIGHTER_ACCOUNT), config.lighterRelayAccounts)
    }

    @Test
    fun anEntryThatIsNotAnObjectDoesNotDropTheLighterRelayAccount() = runTest {
        val payload = PerpsSecurityBlocks.payload()
        val listed = payload.getValue("relay_depositories").jsonArray
        val broken = JsonObject(
            payload + ("relay_depositories" to JsonArray(listOf(JsonNull, JsonPrimitive(42), JsonPrimitive("eth"), JsonArray(emptyList())) + listed)),
        )
        served = PerpsSecurityBlocks.signed(key, broken)

        val config = checkNotNull(repository.verified())

        assertEquals(setOf(PerpsSecurityBlocks.RELAY_LIGHTER_ACCOUNT), config.lighterRelayAccounts)
    }

    @Test
    fun aBlockWithoutALighterRelayAccountIsStillAConfig() = runTest {
        val blocks = listOf(
            PerpsSecurityBlocks.payload(relayDepositories = emptyList()),
            PerpsSecurityBlocks.payload(relayDepositories = listOf("eth" to JsonPrimitive(PerpsSecurityBlocks.RELAY_EVM_DEPOSITORY))),
            PerpsSecurityBlocks.payload(relayDepositories = listOf("lighter" to JsonPrimitive("relay"))),
        )

        blocks.forEach { payload ->
            served = PerpsSecurityBlocks.signed(key, payload)

            val config = checkNotNull(repository.verified())

            assertEquals("$payload", PerpsSecurityBlocks.LIGHTER_CHANGE_PUB_KEY, config.changePubKeyContract)
            assertTrue("$payload", config.lighterRelayAccounts.isEmpty())
        }
    }

    @Test
    fun theBlockServedBeforeLighterMovedChangePubKeyIsNoConfig() = runTest {
        val production = PerpsSecurityConfigRepository(servedBlock = { PRODUCTION_BLOCK_V1 }, store = store)

        assertNull(production.verified())
        assertNull(store.block)
    }

    @Test
    fun aServedBlockSignedByTheTrustedKeyIsReadAndKept() = runTest {
        served = PerpsSecurityBlocks.signed(key)

        val config = checkNotNull(repository.verified())

        assertEquals(PerpsSecurityBlocks.HERMES_DELEGATE, config.hermesDelegate)
        assertTrue(config.delegatesOnEthereum)
        assertEquals(served, store.block)
    }

    @Test
    fun nothingVerifiedIsNoConfig() = runTest {
        val refused = listOf(
            null,
            "",
            "[]",
            PerpsSecurityBlocks.signed(PerpsSecurityBlocks.newKey()),
            PerpsSecurityBlocks.signed(key, keyVersion = 2),
            PerpsSecurityBlocks.signed(key, servedPayload = PerpsSecurityBlocks.payload(delegate = OTHER_ADDRESS).toString().toByteArray()),
            PerpsSecurityBlocks.signed(key, PerpsSecurityBlocks.payload(network = "testnet")),
            PerpsSecurityBlocks.signed(key, PerpsSecurityBlocks.payload(changePubKey = null)),
            PerpsSecurityBlocks.signed(key, PerpsSecurityBlocks.payload(changePubKeyChain = "arb")),
            PerpsSecurityBlocks.signed(key, PerpsSecurityBlocks.payload(changePubKey = "0x183ef7")),
            PerpsSecurityBlocks.signed(key, PerpsSecurityBlocks.payload(delegate = "0x24b576")),
            PerpsSecurityBlocks.signed(key, PerpsSecurityBlocks.payload(version = 0)),
        )

        refused.forEach { block ->
            served = block

            assertNull("$block", repository.verified())
            assertNull("$block", store.block)
        }
    }

    @Test
    fun aBlockThatFailsItsSignatureLeavesTheKeptOneInForce() = runTest {
        served = PerpsSecurityBlocks.signed(key)
        repository.verified()
        val kept = store.block

        served = PerpsSecurityBlocks.signed(PerpsSecurityBlocks.newKey(), PerpsSecurityBlocks.payload(delegate = OTHER_ADDRESS))
        val config = checkNotNull(repository.verified())

        assertEquals(PerpsSecurityBlocks.HERMES_DELEGATE, config.hermesDelegate)
        assertEquals(kept, store.block)
    }

    @Test
    fun anOlderVersionThanTheKeptOneIsRefused() = runTest {
        served = PerpsSecurityBlocks.signed(key, PerpsSecurityBlocks.payload(version = 2))
        repository.verified()
        val kept = store.block

        served = PerpsSecurityBlocks.signed(key, PerpsSecurityBlocks.payload(version = 1, delegate = OTHER_ADDRESS))
        val config = checkNotNull(repository.verified())

        assertEquals(2L, config.version)
        assertEquals(PerpsSecurityBlocks.HERMES_DELEGATE, config.hermesDelegate)
        assertEquals(kept, store.block)
    }

    @Test
    fun aNewerVersionReplacesTheKeptOne() = runTest {
        served = PerpsSecurityBlocks.signed(key, PerpsSecurityBlocks.payload(version = 1))
        repository.verified()

        served = PerpsSecurityBlocks.signed(key, PerpsSecurityBlocks.payload(version = 2, delegate = OTHER_ADDRESS))
        val config = checkNotNull(repository.verified())

        assertEquals(2L, config.version)
        assertEquals(OTHER_ADDRESS, config.hermesDelegate)
        assertEquals(served, store.block)
    }

    @Test
    fun aKeptBlockAlteredInStorageIsNoConfig() = runTest {
        store.block = PerpsSecurityBlocks.signed(
            key,
            servedPayload = PerpsSecurityBlocks.payload(delegate = OTHER_ADDRESS).toString().toByteArray(),
        )

        assertNull(repository.verified())
    }

    @Test
    fun theBlockWithMarketsTheBackendServesTodayListsEveryMarketWithItsScaleAndLimits() = runTest {
        val block = checkNotNull(PerpsSecurityConfigRepositoryTest::class.java.getResource(PRODUCTION_BLOCK_WITH_MARKETS)).readText()
        val production = PerpsSecurityConfigRepository(servedBlock = { block }, store = store)

        val config = checkNotNull(production.verified())

        assertEquals(2L, config.version)
        assertEquals(243, config.markets.size)
        val sol = checkNotNull(config.markets[2])
        assertEquals("SOL", sol.symbol)
        assertEquals(3, sol.priceDecimals)
        assertEquals(3, sol.sizeDecimals)
        assertEquals(6, sol.quoteDecimals)
        assertEquals(BigDecimal("0.055"), sol.minBaseAmount)
        assertEquals(BigDecimal("10.000000"), sol.minQuoteAmount)
        assertEquals(25, sol.maxLeverage)
        assertEquals(0, checkNotNull(config.markets[28]).maxLeverage)
        assertEquals("ASELS", checkNotNull(config.markets[8286]).symbol)
    }

    @Test
    fun theBlockWithRelayRoutersTheBackendServesTodayNamesEachByItsRoleOnEveryEvmNetwork() = runTest {
        val block = checkNotNull(PerpsSecurityConfigRepositoryTest::class.java.getResource(PRODUCTION_BLOCK_WITH_RELAY_ROUTERS)).readText()
        val production = PerpsSecurityConfigRepository(servedBlock = { block }, store = store)

        val config = checkNotNull(production.verified())

        val roles = mapOf(
            PerpsSecurityBlocks.RELAY_ERC20_ROUTER to PerpsRelayRouter.Erc20Router,
            PerpsSecurityBlocks.RELAY_APPROVAL_PROXY to PerpsRelayRouter.ApprovalProxy,
            PerpsSecurityBlocks.RELAY_RECEIVER to PerpsRelayRouter.RelayReceiver,
        )
        val expected = listOf("arb", "avax", "base", "bsc", "eth", "polygon").associateWith { roles } +
            ("robinhood" to roles - PerpsSecurityBlocks.RELAY_RECEIVER)
        assertEquals(expected, config.evmRelayRouters)
        assertEquals(expected.keys, config.evmRelayDepositories.keys)
        assertEquals(setOf(PerpsSecurityBlocks.RELAY_TON_DEPOSITORY), config.tonRelayDepositories)
    }

    @Test
    fun onlyAnEvmAddressSignedWithOneKnownRoleIsARelayRouter() = runTest {
        served = PerpsSecurityBlocks.signed(
            key,
            PerpsSecurityBlocks.payload(
                relayRouters = listOf(
                    PerpsSecurityBlocks.relayRouter("eth", PerpsSecurityBlocks.RELAY_ERC20_ROUTER.uppercase().replace("0X", "0x"), "erc20_router"),
                    PerpsSecurityBlocks.relayRouter("eth", PerpsSecurityBlocks.RELAY_ERC20_ROUTER, "erc20_router"),
                    PerpsSecurityBlocks.relayRouter("eth", PerpsSecurityBlocks.RELAY_APPROVAL_PROXY, "approval_proxy"),
                    PerpsSecurityBlocks.relayRouter("eth", PerpsSecurityBlocks.RELAY_APPROVAL_PROXY, "erc20_router"),
                    PerpsSecurityBlocks.relayRouter("eth", PerpsSecurityBlocks.RELAY_RECEIVER, "receiver"),
                    PerpsSecurityBlocks.relayRouter("eth", "0x1234", "erc20_router"),
                    PerpsSecurityBlocks.relayRouter("tron", TRON_DEPOSITORY, "erc20_router"),
                    PerpsSecurityBlocks.relayRouter("bsc", PerpsSecurityBlocks.RELAY_RECEIVER, "RELAY_RECEIVER"),
                    buildJsonObject {
                        put("chain", "base")
                        put("address", PerpsSecurityBlocks.RELAY_RECEIVER)
                    },
                    buildJsonObject {
                        put("chain", "base")
                        put("address", PerpsSecurityBlocks.RELAY_ERC20_ROUTER)
                        put("type", 1)
                    },
                    buildJsonObject {
                        put("address", PerpsSecurityBlocks.RELAY_ERC20_ROUTER)
                        put("type", "erc20_router")
                    },
                    PerpsSecurityBlocks.relayRouter("arb", PerpsSecurityBlocks.RELAY_RECEIVER, "relay_receiver"),
                ),
            ),
        )

        val config = checkNotNull(repository.verified())

        assertEquals(
            mapOf(
                "eth" to mapOf(PerpsSecurityBlocks.RELAY_ERC20_ROUTER to PerpsRelayRouter.Erc20Router),
                "arb" to mapOf(PerpsSecurityBlocks.RELAY_RECEIVER to PerpsRelayRouter.RelayReceiver),
            ),
            config.evmRelayRouters,
        )
    }

    @Test
    fun aBlockWithoutMarketsIsStillAConfigThatListsNone() = runTest {
        served = PerpsSecurityBlocks.signed(key, PerpsSecurityBlocks.payload(markets = null))

        val config = checkNotNull(repository.verified())

        assertTrue(config.markets.isEmpty())
        assertEquals(setOf(PerpsSecurityBlocks.RELAY_LIGHTER_ACCOUNT), config.lighterRelayAccounts)
    }

    @Test
    fun onlyAFullyReadableMarketListedOnceIsAMarket() = runTest {
        val listed = PerpsSecurityBlocks.market(marketId = 2, symbol = "SOL", priceDecimals = 3, sizeDecimals = 3)
        served = PerpsSecurityBlocks.signed(
            key,
            PerpsSecurityBlocks.payload(
                markets = listOf(
                    listed,
                    PerpsSecurityBlocks.market(marketId = 7),
                    PerpsSecurityBlocks.market(marketId = 7, symbol = "XRP"),
                    JsonObject(PerpsSecurityBlocks.market(marketId = 8) - "quote_decimals"),
                    JsonObject(PerpsSecurityBlocks.market(marketId = 9) + ("price_decimals" to JsonPrimitive("5"))),
                    JsonObject(PerpsSecurityBlocks.market(marketId = 10) + ("min_base_amount" to JsonPrimitive(0.1))),
                    PerpsSecurityBlocks.market(marketId = 11, minQuoteAmount = "1e1"),
                    PerpsSecurityBlocks.market(marketId = 12, minBaseAmount = "-0.1"),
                    PerpsSecurityBlocks.market(marketId = 13, sizeDecimals = 19),
                    PerpsSecurityBlocks.market(marketId = 14, maxLeverage = -1),
                    PerpsSecurityBlocks.market(marketId = -1),
                    PerpsSecurityBlocks.market(marketId = 15, symbol = " "),
                    JsonObject(PerpsSecurityBlocks.market(marketId = 16) + ("max_leverage" to JsonPrimitive(2.5))),
                    PerpsSecurityBlocks.market(marketId = 17, maxLeverage = 10_001),
                    PerpsSecurityBlocks.market(marketId = 18, minQuoteAmount = "1." + "0".repeat(70)),
                    PerpsSecurityBlocks.market(marketId = Short.MAX_VALUE + 1),
                ),
            ),
        )

        val config = checkNotNull(repository.verified())

        assertEquals(setOf(2), config.markets.keys)
        assertEquals(3, checkNotNull(config.markets[2]).priceDecimals)
    }

    @Test
    fun theLargestMarketIdASignedOrderCarriesIsStillAMarket() = runTest {
        served = PerpsSecurityBlocks.signed(
            key,
            PerpsSecurityBlocks.payload(markets = listOf(PerpsSecurityBlocks.market(marketId = Short.MAX_VALUE.toInt()))),
        )

        val config = checkNotNull(repository.verified())

        assertEquals(setOf(Short.MAX_VALUE.toInt()), config.markets.keys)
    }

    @Test
    fun withNothingServedOrKeptTheConfigDownloadIsAwaitedAndItsBlockRead() = runTest {
        var downloads = 0
        val repository = repositoryDownloading {
            downloads++
            served = PerpsSecurityBlocks.signed(key)
        }

        val config = checkNotNull(repository.verified())

        assertEquals(PerpsSecurityBlocks.HERMES_DELEGATE, config.hermesDelegate)
        assertEquals(1, downloads)
        assertEquals(served, store.block)
    }

    @Test
    fun aCallWhileTheConfigDownloadIsInFlightWaitsForItInsteadOfRefusing() = runTest {
        val downloaded = CompletableDeferred<Unit>()
        val repository = repositoryDownloading {
            downloaded.await()
            served = PerpsSecurityBlocks.signed(key)
        }

        val config = async { repository.verified() }
        runCurrent()
        assertFalse(config.isCompleted)

        downloaded.complete(Unit)

        assertEquals(PerpsSecurityBlocks.HERMES_DELEGATE, checkNotNull(config.await()).hermesDelegate)
    }

    @Test
    fun aServedBlockThatFailsItsSignatureWithNothingKeptAlsoWaitsForTheDownload() = runTest {
        served = PerpsSecurityBlocks.signed(PerpsSecurityBlocks.newKey())
        val repository = repositoryDownloading {
            served = PerpsSecurityBlocks.signed(key)
        }

        assertEquals(PerpsSecurityBlocks.HERMES_DELEGATE, checkNotNull(repository.verified()).hermesDelegate)
    }

    @Test
    fun aKeptBlockIsReadWithoutWaitingForTheConfigDownload() = runTest {
        store.block = PerpsSecurityBlocks.signed(key)
        var downloads = 0
        val repository = repositoryDownloading { downloads++ }

        assertEquals(PerpsSecurityBlocks.HERMES_DELEGATE, checkNotNull(repository.verified()).hermesDelegate)
        assertEquals(0, downloads)
    }

    @Test
    fun aKeptBlockWithoutWhatTheCallerNeedsWaitsForTheConfigDownloadAndReadsItsBlock() = runTest {
        store.block = PerpsSecurityBlocks.signed(key)
        var downloads = 0
        val repository = repositoryDownloading {
            downloads++
            served = PerpsSecurityBlocks.signed(key, PerpsSecurityBlocks.payload(relayRouters = PerpsSecurityBlocks.relayRouters("eth")))
        }

        val config = checkNotNull(repository.verified { it.evmRelayRouters.isNotEmpty() })

        assertEquals(setOf("eth"), config.evmRelayRouters.keys)
        assertEquals(1, downloads)
    }

    @Test
    fun aConfigDownloadThatStillLacksWhatTheCallerNeedsLeavesTheKeptBlock() = runTest {
        store.block = PerpsSecurityBlocks.signed(key)
        val repository = repositoryDownloading {}

        val config = checkNotNull(repository.verified { it.evmRelayRouters.isNotEmpty() })

        assertTrue(config.evmRelayRouters.isEmpty())
        assertEquals(PerpsSecurityBlocks.HERMES_DELEGATE, config.hermesDelegate)
    }

    @Test
    fun aConfigDownloadThatBringsNoBlockIsNoConfig() = runTest {
        val repository = repositoryDownloading {}

        assertNull(repository.verified())
        assertNull(store.block)
    }

    @Test
    fun aBlockTheNextDownloadBringsIsReadByTheNextCall() = runTest {
        var reachable = false
        var downloads = 0
        val repository = repositoryDownloading {
            downloads++
            if (reachable) {
                served = PerpsSecurityBlocks.signed(key)
            }
        }
        assertNull(repository.verified())

        reachable = true

        assertEquals(PerpsSecurityBlocks.HERMES_DELEGATE, checkNotNull(repository.verified()).hermesDelegate)
        assertEquals(2, downloads)
    }

    private fun repositoryDownloading(awaitDownloadedConfig: suspend () -> Unit) = PerpsSecurityConfigRepository(
        servedBlock = { served },
        store = store,
        trustedKeys = PerpsSecurityBlocks.trusting(key),
        awaitDownloadedConfig = awaitDownloadedConfig,
    )

    private class MemoryStore : PerpsSecurityConfigStore {

        var block: String? = null

        override fun keptBlock(): String? = block

        override fun keep(block: String) {
            this.block = block
        }
    }

    private companion object {
        const val OTHER_ADDRESS = "0xd1c44e466b70002ac54fb727ed7cbe8f92c782f1"
        const val PRODUCTION_BLOCK_WITH_MARKETS = "production_block_markets.json"
        const val PRODUCTION_BLOCK_WITH_RELAY_ROUTERS = "production_block_relay_routers.json"
        const val PRODUCTION_BLOCK_V1 = """{"payload":"eyJ2ZXJzaW9uIjoxLCJpc3N1ZWRfYXQiOjE3OTQyMjM5OTYsIm5ldHdvcmsiOiJtYWlubmV0IiwiaG9zdHMiOnsicGVycHMiOiJodHRwczovL2FwcC5nZW8udG9ua2VlcGVyLmNvbS90ay1wZXJwcy9wdWJsaWMtYXBpL3YxIiwia2FuZGVsYWJyIjoiaHR0cHM6Ly9hcHAuZ2VvLnRvbmtlZXBlci5jb20va2FuZGVsYWJyL3B1YmxpYy1hcGkvdjEiLCJoZXJtZXMiOiJ3c3M6Ly9hcHAuZ2VvLnRvbmtlZXBlci5jb20vaGVybWVzL3B1YmxpYy1hcGkvdjEvd3MiLCJsaWdodGVyIjoiaHR0cHM6Ly9tYWlubmV0LnprbGlnaHRlci5lbGxpb3QuYWkifSwiZGV2aWNlX2F1dGhfaG9zdHMiOlsiYXBwLmdlby50b25rZWVwZXIuY29tIiwic3dhcC50b25rZWVwZXIuY29tIl0sImNvbnRyYWN0cyI6eyJsaWdodGVyX2dhdGV3YXkiOnsiY2hhaW4iOiJldGgiLCJhZGRyZXNzIjoiMHgzYjRkNzk0YTY2MzA0ZjEzMGE0ZGI4ZjI1NTFiMDA3MGRmY2Y1Y2E3In0sImhlcm1lc19kZWxlZ2F0ZSI6eyJhZGRyZXNzIjoiMHgyNGI1NzZiYzI3MTgyM2JmOWMyNGJlNjI3YjQzNjNiOWIwMDE5MWU5IiwiY2hhaW5zIjpbImV0aCIsImJzYyIsImFyYiIsImJhc2UiLCJwb2x5Z29uIiwicm9iaW5ob29kIl19fSwiaW50ZWdyYXRvcnMiOlt7ImludGVncmF0b3JfYWNjb3VudF9pbmRleCI6MjgxNDc0OTc2NDg3NzY0LCJtYXhfcGVycHNfdGFrZXJfZmVlIjoxMDAwLCJtYXhfcGVycHNfbWFrZXJfZmVlIjoxMDAwLCJtYXhfc3BvdF90YWtlcl9mZWUiOjAsIm1heF9zcG90X21ha2VyX2ZlZSI6MCwibWF4X2FwcHJvdmFsX3R0bF9tcyI6MzE1MzYwMDAwMDB9LHsiaW50ZWdyYXRvcl9hY2NvdW50X2luZGV4IjoyODE0NzQ5NzY0ODc3NjksIm1heF9wZXJwc190YWtlcl9mZWUiOjEwMDAsIm1heF9wZXJwc19tYWtlcl9mZWUiOjEwMDAsIm1heF9zcG90X3Rha2VyX2ZlZSI6MCwibWF4X3Nwb3RfbWFrZXJfZmVlIjowLCJtYXhfYXBwcm92YWxfdHRsX21zIjozMTUzNjAwMDAwMH0seyJpbnRlZ3JhdG9yX2FjY291bnRfaW5kZXgiOjI4MTQ3NDk3NjQ4Nzc3MCwibWF4X3BlcnBzX3Rha2VyX2ZlZSI6MTAwMCwibWF4X3BlcnBzX21ha2VyX2ZlZSI6MTAwMCwibWF4X3Nwb3RfdGFrZXJfZmVlIjowLCJtYXhfc3BvdF9tYWtlcl9mZWUiOjAsIm1heF9hcHByb3ZhbF90dGxfbXMiOjMxNTM2MDAwMDAwfV19","signature":"PiPHnzcZxVlcJYBuEU7PcXEduVe1yqTnw9NgHI6gJ1YY2gYxfZN8lS5JAid9lQwo3Z3j6LqlLLCMk+XeFvvSCw==","key_version":1}"""
        const val OTHER_TON_ADDRESS = "UQC1zHXDFDKegQmkRuPjT9G6mZ-9pDVp89zUWrXA3WfyqMJm"
        const val TRON_DEPOSITORY = "TXtEs6t2oUWQsNos7m68gbHdE9Q5n6x2oN"
        const val OTHER_BTC_ADDRESS = "bc1qnmmy8w4kf2ews60wzyyafnxuvyteesmypqtpsn"
        const val LEGACY_BTC_ADDRESS = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"
        const val PRODUCTION_BLOCK_V2 = """{"payload":"eyJ2ZXJzaW9uIjoyLCJpc3N1ZWRfYXQiOjE3OTQyNDc1MjEsIm5ldHdvcmsiOiJtYWlubmV0IiwiaG9zdHMiOnsicGVycHMiOiJodHRwczovL2FwcC5nZW8udG9ua2VlcGVyLmNvbS90ay1wZXJwcy9wdWJsaWMtYXBpL3YxIiwia2FuZGVsYWJyIjoiaHR0cHM6Ly9hcHAuZ2VvLnRvbmtlZXBlci5jb20va2FuZGVsYWJyL3B1YmxpYy1hcGkvdjEiLCJoZXJtZXMiOiJ3c3M6Ly9hcHAuZ2VvLnRvbmtlZXBlci5jb20vaGVybWVzL3B1YmxpYy1hcGkvdjEvd3MiLCJsaWdodGVyIjoiaHR0cHM6Ly9tYWlubmV0LnprbGlnaHRlci5lbGxpb3QuYWkifSwiZGV2aWNlX2F1dGhfaG9zdHMiOlsiYXBwLmdlby50b25rZWVwZXIuY29tIiwic3dhcC50b25rZWVwZXIuY29tIl0sImNvbnRyYWN0cyI6eyJsaWdodGVyX2dhdGV3YXkiOnsiY2hhaW4iOiJldGgiLCJhZGRyZXNzIjoiMHgzYjRkNzk0YTY2MzA0ZjEzMGE0ZGI4ZjI1NTFiMDA3MGRmY2Y1Y2E3In0sImxpZ2h0ZXJfY2hhbmdlX3B1Yl9rZXkiOnsiY2hhaW4iOiJldGgiLCJhZGRyZXNzIjoiMHgxODNlZjcxOTVkNDQzYTNkYTdlYzFkNzAwM2JhY2ExZmY0OTA3NTdkIn0sImhlcm1lc19kZWxlZ2F0ZSI6eyJhZGRyZXNzIjoiMHgyNGI1NzZiYzI3MTgyM2JmOWMyNGJlNjI3YjQzNjNiOWIwMDE5MWU5IiwiY2hhaW5zIjpbImV0aCIsImJzYyIsImFyYiIsImJhc2UiLCJwb2x5Z29uIiwicm9iaW5ob29kIl19fSwicmVsYXlfZGVwb3NpdG9yaWVzIjpbeyJjaGFpbiI6ImFyYiIsImFkZHJlc3MiOiIweDRjZDAwZTM4NzYyMmMzNWJkZGI5YjRjOTYyYzEzNjQ2MjMzOGJjMzEifSx7ImNoYWluIjoiYXZheCIsImFkZHJlc3MiOiIweDRjZDAwZTM4NzYyMmMzNWJkZGI5YjRjOTYyYzEzNjQ2MjMzOGJjMzEifSx7ImNoYWluIjoiYmFzZSIsImFkZHJlc3MiOiIweDRjZDAwZTM4NzYyMmMzNWJkZGI5YjRjOTYyYzEzNjQ2MjMzOGJjMzEifSx7ImNoYWluIjoiYnNjIiwiYWRkcmVzcyI6IjB4NGNkMDBlMzg3NjIyYzM1YmRkYjliNGM5NjJjMTM2NDYyMzM4YmMzMSJ9LHsiY2hhaW4iOiJidGMiLCJhZGRyZXNzIjoiYmMxcXptdG4wcTkyYXllanQyaHBmZnZsa3RjcG15eTd2dnNkMDZzZWZ1In0seyJjaGFpbiI6ImV0aCIsImFkZHJlc3MiOiIweDRjZDAwZTM4NzYyMmMzNWJkZGI5YjRjOTYyYzEzNjQ2MjMzOGJjMzEifSx7ImNoYWluIjoibGlnaHRlciIsImFkZHJlc3MiOiI3MzEwMzMifSx7ImNoYWluIjoicG9seWdvbiIsImFkZHJlc3MiOiIweDRjZDAwZTM4NzYyMmMzNWJkZGI5YjRjOTYyYzEzNjQ2MjMzOGJjMzEifSx7ImNoYWluIjoicm9iaW5ob29kIiwiYWRkcmVzcyI6IjB4NGNkMDBlMzg3NjIyYzM1YmRkYjliNGM5NjJjMTM2NDYyMzM4YmMzMSJ9LHsiY2hhaW4iOiJ0b24iLCJhZGRyZXNzIjoiMDphYjc0NmIwMzRlYTAzNmI3YWM1MTQ3ODM3YTU3ODI3YmQzOTE3YmMzZjU1MDZkNDc0NzllODdlYzg3ZWYyNTU0In0seyJjaGFpbiI6InRyb24iLCJhZGRyZXNzIjoiVFh0RXM2dDJvVVdRc05vczdtNjhnYkhkRTlRNW42eDJvTiJ9XSwiaW50ZWdyYXRvcnMiOlt7ImludGVncmF0b3JfYWNjb3VudF9pbmRleCI6MjgxNDc0OTc2NDg3NzY0LCJtYXhfcGVycHNfdGFrZXJfZmVlIjoxMDAwLCJtYXhfcGVycHNfbWFrZXJfZmVlIjoxMDAwLCJtYXhfc3BvdF90YWtlcl9mZWUiOjAsIm1heF9zcG90X21ha2VyX2ZlZSI6MCwibWF4X2FwcHJvdmFsX3R0bF9tcyI6MzE1MzYwMDAwMDB9LHsiaW50ZWdyYXRvcl9hY2NvdW50X2luZGV4IjoyODE0NzQ5NzY0ODc3NjksIm1heF9wZXJwc190YWtlcl9mZWUiOjEwMDAsIm1heF9wZXJwc19tYWtlcl9mZWUiOjEwMDAsIm1heF9zcG90X3Rha2VyX2ZlZSI6MCwibWF4X3Nwb3RfbWFrZXJfZmVlIjowLCJtYXhfYXBwcm92YWxfdHRsX21zIjozMTUzNjAwMDAwMH0seyJpbnRlZ3JhdG9yX2FjY291bnRfaW5kZXgiOjI4MTQ3NDk3NjQ4Nzc3MCwibWF4X3BlcnBzX3Rha2VyX2ZlZSI6MTAwMCwibWF4X3BlcnBzX21ha2VyX2ZlZSI6MTAwMCwibWF4X3Nwb3RfdGFrZXJfZmVlIjowLCJtYXhfc3BvdF9tYWtlcl9mZWUiOjAsIm1heF9hcHByb3ZhbF90dGxfbXMiOjMxNTM2MDAwMDAwfV19","signature":"e3Ayj5gCn/+xtsErJAAf/Zwx3thinwW27+Q2y/luBXVSzkS2awo/RPQHNiUVPVunuz4dZHpjg1HCdJ1xi579AA==","key_version":1}"""
    }
}
