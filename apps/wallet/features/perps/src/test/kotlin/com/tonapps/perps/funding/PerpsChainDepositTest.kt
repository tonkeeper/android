package com.tonapps.perps.funding

import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.generated.Events.Perps.PerpsSourceScreen
import com.ionspin.kotlin.bignum.integer.BigInteger as ChainInteger
import com.tonapps.chainkit.core.chain.api.models.ChainError
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.CHANGE_PUB_KEY_CONTRACT
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.DEPOSIT_EXECUTION_ID
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.EVM_NONCE
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GATEWAY
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.HERMES_DELEGATE
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.L1_ADDRESS
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.RELAY_DEPOSITORY
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.TRADING_PUBLIC_KEY
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.TRON_RELAY_DEPOSITORY
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.USDC
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.USDC_TOKEN
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.WALLET_ID
import com.tonapps.perps.security.PerpsSecurityBlocks
import com.tonapps.wallet.data.multichain.lighter.PerpsQuoteError
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.testing.testAsset
import io.mockk.coEvery
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

class PerpsChainDepositTest : BaseUnitTest() {

    private lateinit var env: PerpsFundingTestEnv

    @Before
    fun setUp() {
        env = PerpsFundingTestEnv()
        env.accounts[BSC_USDT.id] = testAccount(BSC_USDT, balance = (USDT_AMOUNT * BigInteger.TEN).toString())
        env.accounts[BNB.id] = testAccount(BNB, balance = (BNB_AMOUNT * BigInteger.TEN).toString())
        env.accounts[TestAssets.usdtEth.id] = testAccount(TestAssets.usdtEth, balance = (USDT_ETH_AMOUNT * BigInteger.TEN).toString())
        env.accounts[TestAssets.trx.id] = testAccount(TestAssets.trx, balance = (TRX_AMOUNT * BigInteger.TEN).toString())
        env.accounts[TestAssets.usdtTron.id] = testAccount(TestAssets.usdtTron, balance = (TRON_USDT_AMOUNT * BigInteger.TEN).toString())
        env.accounts[USDC.id] = testAccount(USDC, balance = (USDC_AMOUNT * BigInteger.TEN).toString())
        env.accounts[AVALANCHE_USDT.id] = testAccount(AVALANCHE_USDT, balance = (AVALANCHE_USDT_AMOUNT * BigInteger.TEN).toString())
        env.servedSecurityBlock = env.signedSecurityBlock(securityPayload())
    }

    @Test
    fun aTokenDepositFoldedIntoOneBatchOpensWithTheAmountOfItsDepositCall() = runTest {
        quote("relay") { listOf(bscBatch()) }

        val deposit = deposit(BSC_USDT.id, USDT_AMOUNT)

        assertEquals("Relay", deposit.verifiedPayment?.provider)
        assertEquals(USDT_AMOUNT.toString(), deposit.verifiedPayment?.amount.toString())
    }

    @Test
    fun aTokenDepositAsItsApprovalAndItsCallOpens() = runTest {
        quote("relay") {
            listOf(
                bscPayload("approve", to = BSC_USDT_TOKEN, data = env.approveData(RELAY_DEPOSITORY, USDT_AMOUNT)),
                bscPayload("deposit", to = RELAY_DEPOSITORY, data = env.tokenDepositData(BSC_USDT_TOKEN, USDT_AMOUNT)),
            )
        }

        val deposit = deposit(BSC_USDT.id, USDT_AMOUNT)

        assertEquals(USDT_AMOUNT.toString(), deposit.verifiedPayment?.amount.toString())
    }

    @Test
    fun aCoinDepositOpensWithTheValueItSends() = runTest {
        val sent = BNB_AMOUNT * BigInteger.valueOf(995) / BigInteger.valueOf(1000)
        quote("relay") { listOf(bscPayload("pay", to = RELAY_DEPOSITORY, value = sent, data = env.nativeDepositData())) }

        val deposit = deposit(BNB.id, BNB_AMOUNT)

        assertEquals(BNB_AMOUNT.toString(), deposit.amount.toString())
        assertEquals(sent.toString(), deposit.verifiedPayment?.amount.toString())
    }

    @Test
    fun tronDepositsOpenAsCallsOfRelaysTronDepository() = runTest {
        quote("relay") { listOf(env.tronPayload("pay", to = TRON_RELAY_DEPOSITORY, data = env.tronNativeDepositData(), value = TRX_AMOUNT)) }
        assertEquals(TRX_AMOUNT.toString(), deposit(TestAssets.trx.id, TRX_AMOUNT).verifiedPayment?.amount.toString())

        quote("relay") {
            listOf(
                env.tronPayload("approve", to = TRON_USDT, data = env.tronApproveData(TRON_RELAY_DEPOSITORY, TRON_USDT_AMOUNT)),
                env.tronPayload("deposit", to = TRON_RELAY_DEPOSITORY, data = env.tronTokenDepositData(TRON_USDT, TRON_USDT_AMOUNT)),
            )
        }
        assertEquals(TRON_USDT_AMOUNT.toString(), deposit(TestAssets.usdtTron.id, TRON_USDT_AMOUNT).verifiedPayment?.amount.toString())
    }

    @Test
    fun aTronTokenDepositAddsTheBandwidthToTheQuotedFeeOfTheCallItCannotSimulateBeforeTheApproval() = runTest {
        quote("relay") {
            listOf(
                env.tronPayload("approve", to = TRON_USDT, data = env.tronApproveData(TRON_RELAY_DEPOSITORY, TRON_USDT_AMOUNT)),
                env.tronPayload("deposit", to = TRON_RELAY_DEPOSITORY, data = env.tronTokenDepositData(TRON_USDT, TRON_USDT_AMOUNT), fee = TRON_QUOTED_FEE),
            )
        }
        coEvery { env.provider.estimateFee(any<Payload>(), any(), any()) } answers {
            val payload = firstArg<Payload>()
            when {
                payload.fee != null -> ChainRes.ok(checkNotNull(payload.fee))
                payload.id == "deposit" -> ChainRes.err(ChainError.Unknown(IllegalStateException("The contract call fails in simulation")))
                else -> ChainRes.ok(Fee.Value(Chain.Tron.Mainnet.toAsset(), ChainInteger.parseString(TRON_BANDWIDTH_FEE.toString())))
            }
        }

        val payloads = deposit(TestAssets.usdtTron.id, TRON_USDT_AMOUNT).operation.payloads

        assertNull(payloads.first { it.id == "approve" }.fee)
        assertEquals((TRON_QUOTED_FEE + TRON_BANDWIDTH_FEE).toString(), payloads.first { it.id == "deposit" }.fee?.amount?.toString())
    }

    @Test
    fun usdcOnEthereumOpensAsTheGatewayDepositOfTheAmountEntered() = runTest {
        quote("direct") { listOf(directBatch()) }

        val deposit = deposit(USDC.id, USDC_AMOUNT)

        assertNull(deposit.verifiedPayment)
    }

    @Test
    fun aFirstUsdcDepositRegistersTheKeyAfterTheGatewayDeposit() = runTest {
        env.withoutAccount()
        quote("direct") {
            listOf(
                directBatch(
                    extra = Triple(CHANGE_PUB_KEY_CONTRACT, BigInteger.ZERO, env.registrationCalldata(TRADING_PUBLIC_KEY)),
                    authorizationNonce = EVM_NONCE + 1,
                ),
            )
        }

        val deposit = deposit(USDC.id, USDC_AMOUNT)

        assertNull(deposit.verifiedPayment)
    }

    @Test
    fun usdcThroughLightersBridgeOpensAsOneTransferOfTheAmountEntered() = runTest {
        quote("funxyz") { listOf(env.evmPayload("pay", to = USDC_TOKEN, data = transferData(BRIDGE_ADDRESS, USDC_AMOUNT))) }

        val deposit = deposit(USDC.id, USDC_AMOUNT)

        assertNull(deposit.verifiedPayment)
    }

    @Test
    fun aFirstDepositFromEthereumGoesThroughRelaysRouterAsTheBackendBuildsIt() = runTest {
        env.withoutAccount()
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) { asked ->
            if (asked.registersKey()) {
                listOf(env.hermesPayload("execute"))
            } else {
                listOf(routedTokenPayment(RELAY_APPROVAL_PROXY, USDT_ETH_AMOUNT))
            }
        }

        val deposit = deposit(TestAssets.usdtEth.id, USDT_ETH_AMOUNT)

        assertEquals(USDT_ETH_AMOUNT.toString(), deposit.verifiedPayment?.amount.toString())
    }

    @Test
    fun aRouterPaymentReadAgainstAConfigCachedBeforeRelaysRoutersWaitsForTheDownloadedOne() = runTest {
        env.servedSecurityBlock = env.signedSecurityBlock(securityPayload(routers = emptyList()))
        var downloads = 0
        env.onConfigDownload = {
            downloads++
            env.servedSecurityBlock = env.signedSecurityBlock(securityPayload())
        }
        quote("relay") { listOf(env.evmPayload("pay", to = RELAY_ROUTER, value = ETH_AMOUNT, data = ROUTER_CALL)) }

        val deposit = deposit(TestAssets.eth.id, ETH_AMOUNT)

        assertEquals(ETH_AMOUNT.toString(), deposit.verifiedPayment?.amount.toString())
        assertTrue(downloads > 0)
    }

    @Test
    fun aRouterPaymentFromANetworkTheCachedConfigHasNoRoutersForWaitsForTheDownloadedOne() = runTest {
        env.servedSecurityBlock = env.signedSecurityBlock(securityPayload(routers = PerpsSecurityBlocks.relayRouters("bsc")))
        var downloads = 0
        env.onConfigDownload = {
            downloads++
            env.servedSecurityBlock = env.signedSecurityBlock(securityPayload())
        }
        quote("relay") { listOf(env.evmPayload("pay", to = RELAY_ROUTER, value = ETH_AMOUNT, data = ROUTER_CALL)) }

        assertEquals(ETH_AMOUNT.toString(), deposit(TestAssets.eth.id, ETH_AMOUNT).verifiedPayment?.amount.toString())
        assertTrue(downloads > 0)
    }

    @Test
    fun aDepositThatCannotGoThroughRelaysRouterDoesNotWaitForTheConfigDownload() = runTest {
        env.servedSecurityBlock = env.signedSecurityBlock(securityPayload(routers = emptyList()))
        var downloads = 0
        env.onConfigDownload = { downloads++ }
        quote("relay") { listOf(env.tronPayload("pay", to = TRON_RELAY_DEPOSITORY, data = env.tronNativeDepositData(), value = TRX_AMOUNT)) }

        assertEquals(TRX_AMOUNT.toString(), deposit(TestAssets.trx.id, TRX_AMOUNT).verifiedPayment?.amount.toString())
        assertEquals(0, downloads)
    }

    @Test
    fun aCoinGoesThroughRelaysRouterForNoMoreThanTheAmount() = runTest {
        quote("relay") { listOf(env.evmPayload("pay", to = RELAY_ROUTER, value = ETH_AMOUNT, data = ROUTER_CALL)) }

        val deposit = deposit(TestAssets.eth.id, ETH_AMOUNT)

        assertEquals(ETH_AMOUNT.toString(), deposit.verifiedPayment?.amount.toString())
    }

    @Test
    fun relaysContractsAreTheOnesTheSignedConfigNamesForTheSourceNetwork() = runTest {
        env.servedSecurityBlock = env.signedSecurityBlock(
            securityPayload(routers = listOf(PerpsSecurityBlocks.relayRouter("bsc", OTHER_ROUTER, "erc20_router"))),
        )
        quote("relay") { listOf(bscPayload("pay", to = OTHER_ROUTER, value = BNB_AMOUNT, data = ROUTER_CALL)) }
        assertEquals(BNB_AMOUNT.toString(), deposit(BNB.id, BNB_AMOUNT).verifiedPayment?.amount.toString())

        quote("relay") { listOf(bscPayload("pay", to = RELAY_ROUTER, value = BNB_AMOUNT, data = ROUTER_CALL)) }
        assertEquals(PerpsDepositQuoteError.QuoteFailed, failureOf { deposit(BNB.id, BNB_AMOUNT) }.toDepositQuoteError())

        quote("relay") { listOf(env.evmPayload("pay", to = OTHER_ROUTER, value = ETH_AMOUNT, data = ROUTER_CALL)) }
        assertEquals(PerpsDepositQuoteError.QuoteFailed, failureOf { deposit(TestAssets.eth.id, ETH_AMOUNT) }.toDepositQuoteError())
    }

    @Test
    fun aNetworkWithoutSignedRoutersStillDepositsThroughRelaysDepository() = runTest {
        env.servedSecurityBlock = env.signedSecurityBlock(securityPayload(routers = PerpsSecurityBlocks.relayRouters("bsc")))
        quote("relay") { asked ->
            if (asked.string("source_asset") == TestAssets.usdtEth.id) {
                listOf(routedTokenPayment(RELAY_APPROVAL_PROXY, USDT_ETH_AMOUNT))
            } else {
                listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData()))
            }
        }

        val routed = failureOf { deposit(TestAssets.usdtEth.id, USDT_ETH_AMOUNT) }
        assertEquals(PerpsDepositQuoteError.QuoteFailed, routed.toDepositQuoteError())

        assertEquals(ETH_AMOUNT.toString(), deposit(TestAssets.eth.id, ETH_AMOUNT).verifiedPayment?.amount.toString())
    }

    @Test
    fun aCoinIntoRelaysDepositoryOrRouterOpensForDownToNinetySevenPercentOfTheAmount() = runTest {
        relayCoinPayments().forEach { (target, data) ->
            listOf(ETH_BUDGET, BigInteger("989700000000000"), BigInteger("970000000000000")).forEach { sent ->
                quote("relay") { listOf(env.evmPayload("pay", to = target, value = sent, data = data)) }

                val deposit = deposit(TestAssets.eth.id, ETH_BUDGET)

                assertEquals("$target $sent", sent.toString(), deposit.verifiedPayment?.amount.toString())
            }
        }
    }

    @Test
    fun aCoinIntoRelaysDepositoryOrRouterForUnderNinetySevenPercentOrNothingIsNoQuote() = runTest {
        relayCoinPayments().forEach { (target, data) ->
            listOf(BigInteger("969999999999999"), BigInteger.ZERO).forEach { sent ->
                quote("relay") { listOf(env.evmPayload("pay", to = target, value = sent, data = data)) }

                val failure = failureOf { deposit(TestAssets.eth.id, ETH_BUDGET) }

                assertEquals("$target $sent", VALUE_REFUSED, (failure as? PerpsDepositException.QuoteRejected)?.error)
            }
        }
    }

    @Test
    fun aLargeFirstDepositQuotedWithOnePercentSlippageOpensForWhatItSends() = runTest {
        env.accounts[BNB.id] = testAccount(BNB, balance = (LARGE_BNB_BUDGET * BigInteger.TWO).toString())
        quote("relay") { listOf(bscPayload("pay", to = RELAY_DEPOSITORY, value = LARGE_FIRST_BNB_DEPOSIT, data = env.nativeDepositData())) }

        val deposit = deposit(BNB.id, LARGE_BNB_BUDGET)

        assertEquals(LARGE_FIRST_BNB_DEPOSIT.toString(), deposit.verifiedPayment?.amount.toString())
    }

    @Test
    fun aTokenThroughRelaysApprovalProxyWithoutAnApprovalOpensForWhatTheProxyTransfers() = runTest {
        env.servedSecurityBlock = env.signedSecurityBlock(securityPayload(chains = listOf("eth", "bsc", "avax")))
        val call = checkNotNull(PerpsChainDepositTest::class.java.getResource(AVALANCHE_PROXY_CALL)).readText().trim()
        quote("relay") { listOf(env.evmPayload("pay", to = RELAY_APPROVAL_PROXY, data = call, chain = "avax/mainnet")) }

        val deposit = deposit(AVALANCHE_USDT.id, AVALANCHE_USDT_AMOUNT)

        assertEquals("Relay", deposit.verifiedPayment?.provider)
        assertEquals(AVALANCHE_USDT_AMOUNT.toString(), deposit.verifiedPayment?.amount.toString())
    }

    @Test
    fun aTokenThroughRelaysApprovalProxyOpensForDownToNinetySevenPercentWithOrWithoutAnApproval() = runTest {
        listOf(USDT_AMOUNT, USDT_AMOUNT * BigInteger.valueOf(97) / BigInteger.valueOf(100)).forEach { transferred ->
            val call = Triple(RELAY_APPROVAL_PROXY, BigInteger.ZERO, proxyCallData(listOf(BSC_USDT_TOKEN), listOf(transferred)))
            val approval = Triple(BSC_USDT_TOKEN, BigInteger.ZERO, env.approveData(RELAY_APPROVAL_PROXY, transferred))
            listOf(
                listOf(bscPayload("pay", to = RELAY_APPROVAL_PROXY, data = call.third)),
                listOf(bscPayload("batch", to = TestWallet.ETH_ADDRESS, data = env.batchData(approval, call, chain = Chain.Smartchain.Mainnet))),
            ).forEach { payloads ->
                quote("relay") { payloads }

                val deposit = deposit(BSC_USDT.id, USDT_AMOUNT)

                assertEquals("$transferred", transferred.toString(), deposit.verifiedPayment?.amount.toString())
            }
        }
    }

    @Test
    fun anyOtherEvmOrTronOperationIsNoQuote() = runTest {
        val opened = refused().mapNotNull { case ->
            env = PerpsFundingTestEnv()
            setUp()
            case.prepare(env)
            quote(case.provider) { case.payloads() }

            val failure = try {
                deposit(case.assetId, case.amount)
                null
            } catch (e: PerpsDepositException) {
                e
            }

            case.name.takeIf { failure?.toDepositQuoteError() != PerpsDepositQuoteError.QuoteFailed }
        }

        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun aNetworkMissingFromTheSignedConfigRefusesOnlyDepositsFromIt() = runTest {
        env.servedSecurityBlock = env.signedSecurityBlock(securityPayload(chains = listOf("eth")))
        quote("relay") { asked ->
            if (asked.string("source_asset") == BSC_USDT.id) {
                listOf(bscBatch())
            } else {
                listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData()))
            }
        }

        val bsc = failureOf { deposit(BSC_USDT.id, USDT_AMOUNT) }
        assertEquals(PerpsDepositQuoteError.QuoteFailed, bsc.toDepositQuoteError())

        assertEquals(ETH_AMOUNT.toString(), deposit(TestAssets.eth.id, ETH_AMOUNT).verifiedPayment?.amount.toString())
    }

    @Test
    fun aProviderTheClientDoesNotKnowIsNoQuote() = runTest {
        quote("lifi") { listOf(bscBatch()) }

        val failure = failureOf { deposit(BSC_USDT.id, USDT_AMOUNT) }

        assertTrue(failure is PerpsDepositException.QuoteRejected)
    }

    private class Case(
        val name: String,
        val assetId: String,
        val amount: BigInteger,
        val provider: String = "relay",
        val prepare: (PerpsFundingTestEnv) -> Unit = {},
        val payloads: () -> List<JsonObject>,
    )

    private fun refused(): List<Case> {
        val usdt = BSC_USDT.id
        val bnb = BNB.id
        val trx = TestAssets.trx.id
        val tronUsdt = TestAssets.usdtTron.id
        val deposit = { token: String, amount: BigInteger, depositor: String -> env.tokenDepositData(token, amount, depositor) }
        fun batch(vararg calls: Triple<String, BigInteger, String>) = bscPayload("batch", to = TestWallet.ETH_ADDRESS, data = env.batchData(*calls, chain = Chain.Smartchain.Mainnet))
        fun approve(spender: String = RELAY_DEPOSITORY, amount: BigInteger = USDT_AMOUNT) =
            Triple(BSC_USDT_TOKEN, BigInteger.ZERO, env.approveData(spender, amount))
        fun pay(data: String = deposit(BSC_USDT_TOKEN, USDT_AMOUNT, L1_ADDRESS), to: String = RELAY_DEPOSITORY) = Triple(to, BigInteger.ZERO, data)
        return listOf(
            Case("a foreign depository", usdt, USDT_AMOUNT) { listOf(batch(approve(OTHER), pay(to = OTHER))) },
            Case("a token transfer instead of the deposit", usdt, USDT_AMOUNT) {
                listOf(batch(approve(), Triple(BSC_USDT_TOKEN, BigInteger.ZERO, transferData(OTHER, USDT_AMOUNT))))
            },
            Case("another selector on the depository", usdt, USDT_AMOUNT) {
                listOf(batch(approve(), pay(data = "0xa9059cbb" + deposit(BSC_USDT_TOKEN, USDT_AMOUNT, L1_ADDRESS).drop(10))))
            },
            Case("a deposit credited to another depositor", usdt, USDT_AMOUNT) { listOf(batch(approve(), pay(data = deposit(BSC_USDT_TOKEN, USDT_AMOUNT, OTHER)))) },
            Case("a deposit of another token", usdt, USDT_AMOUNT) { listOf(batch(approve(), pay(data = deposit(OTHER, USDT_AMOUNT, L1_ADDRESS)))) },
            Case("a deposit over the amount", usdt, USDT_AMOUNT) {
                listOf(batch(approve(), pay(data = deposit(BSC_USDT_TOKEN, USDT_AMOUNT + BigInteger.ONE, L1_ADDRESS))))
            },
            Case("a deposit well under the amount", usdt, USDT_AMOUNT) {
                listOf(batch(approve(), pay(data = deposit(BSC_USDT_TOKEN, USDT_AMOUNT * BigInteger.valueOf(96) / BigInteger.valueOf(100), L1_ADDRESS))))
            },
            Case("an approval to someone else", usdt, USDT_AMOUNT) { listOf(batch(approve(spender = OTHER), pay())) },
            Case("an approval over the amount", usdt, USDT_AMOUNT) { listOf(batch(approve(amount = USDT_AMOUNT + BigInteger.ONE), pay())) },
            Case("an approval of everything", usdt, USDT_AMOUNT) { listOf(batch(approve(amount = BigInteger.TWO.pow(256) - BigInteger.ONE), pay())) },
            Case("an extra call in the batch", usdt, USDT_AMOUNT) {
                listOf(batch(Triple(BSC_USDT_TOKEN, BigInteger.ZERO, transferData(OTHER, BigInteger.ONE)), approve(), pay()))
            },
            Case("a call into the account's delegate", usdt, USDT_AMOUNT) { listOf(batch(approve(), Triple(HERMES_DELEGATE, BigInteger.ZERO, "0x"))) },
            Case("a delegation to another contract", usdt, USDT_AMOUNT) {
                listOf(
                    bscPayload(
                        "batch",
                        to = TestWallet.ETH_ADDRESS,
                        data = env.batchData(approve(), pay(), chain = Chain.Smartchain.Mainnet),
                        authorizationNonce = EVM_NONCE + 1,
                        authorizationAddress = OTHER,
                    ),
                )
            },
            Case("a delegation on another chain", usdt, USDT_AMOUNT) {
                listOf(
                    bscPayload(
                        "batch",
                        to = TestWallet.ETH_ADDRESS,
                        data = env.batchData(approve(), pay(), chain = Chain.Smartchain.Mainnet),
                        authorizationNonce = EVM_NONCE + 1,
                        authorizationChainId = Chain.Ethereum.Mainnet.number,
                    ),
                )
            },
            Case("a payment on another chain", usdt, USDT_AMOUNT) {
                listOf(env.evmPayload("batch", to = TestWallet.ETH_ADDRESS, data = env.batchData(approve(), pay())))
            },
            Case("a second payment", usdt, USDT_AMOUNT) { listOf(bscBatch(), bscBatch()) },
            Case("coins sent along with a token deposit", usdt, USDT_AMOUNT) { listOf(bscPayload("pay", to = RELAY_DEPOSITORY, value = BigInteger.ONE, data = deposit(BSC_USDT_TOKEN, USDT_AMOUNT, L1_ADDRESS))) },
            Case("a coin deposit to a foreign contract", bnb, BNB_AMOUNT) { listOf(bscPayload("pay", to = OTHER, value = BNB_AMOUNT, data = env.nativeDepositData())) },
            Case("a coin deposit for another depositor", bnb, BNB_AMOUNT) {
                listOf(bscPayload("pay", to = RELAY_DEPOSITORY, value = BNB_AMOUNT, data = env.nativeDepositData(OTHER)))
            },
            Case("a coin deposit over the amount", bnb, BNB_AMOUNT) {
                listOf(bscPayload("pay", to = RELAY_DEPOSITORY, value = BNB_AMOUNT + BigInteger.ONE, data = env.nativeDepositData()))
            },
            Case("a coin deposit with an approval", bnb, BNB_AMOUNT) {
                listOf(
                    bscPayload("approve", to = BSC_USDT_TOKEN, data = env.approveData(RELAY_DEPOSITORY, USDT_AMOUNT)),
                    bscPayload("pay", to = RELAY_DEPOSITORY, value = BNB_AMOUNT, data = env.nativeDepositData()),
                )
            },
            Case("a coin deposit that carries more calldata", bnb, BNB_AMOUNT) {
                listOf(bscPayload("pay", to = RELAY_DEPOSITORY, value = BNB_AMOUNT, data = env.nativeDepositData() + "00"))
            },
            Case("a relayed deposit of USDC on Ethereum", USDC.id, USDC_AMOUNT) {
                listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, data = env.tokenDepositData(USDC_TOKEN, USDC_AMOUNT)))
            },
            Case("a gateway deposit of anything but USDC on Ethereum", usdt, USDT_AMOUNT, provider = "direct") { listOf(bscBatch()) },
            Case("a gateway deposit to another account", USDC.id, USDC_AMOUNT, provider = "direct") {
                listOf(directBatch(deposit = env.gatewayDepositData(OTHER, USDC_AMOUNT)))
            },
            Case("a gateway deposit of another amount", USDC.id, USDC_AMOUNT, provider = "direct") {
                listOf(directBatch(deposit = env.gatewayDepositData(L1_ADDRESS, USDC_AMOUNT - BigInteger.ONE)))
            },
            Case("a gateway deposit on another contract", USDC.id, USDC_AMOUNT, provider = "direct") {
                listOf(env.evmPayload("pay", to = OTHER, data = env.gatewayDepositData(L1_ADDRESS, USDC_AMOUNT)))
            },
            Case("a gateway approval to someone else", USDC.id, USDC_AMOUNT, provider = "direct") {
                listOf(directBatch(approval = env.approveData(OTHER, USDC_AMOUNT)))
            },
            Case("a key registration nobody asked for", USDC.id, USDC_AMOUNT, provider = "direct") {
                listOf(directBatch(extra = Triple(CHANGE_PUB_KEY_CONTRACT, BigInteger.ZERO, env.registrationCalldata(TRADING_PUBLIC_KEY))))
            },
            Case("a gateway deposit without the signed gateway", USDC.id, USDC_AMOUNT, provider = "direct", prepare = {
                it.servedSecurityBlock = it.signedSecurityBlock(securityPayload(gateway = false))
            }) { listOf(directBatch()) },
            Case("a bridge transfer to the sender", USDC.id, USDC_AMOUNT, provider = "funxyz") {
                listOf(env.evmPayload("pay", to = USDC_TOKEN, data = transferData(L1_ADDRESS, USDC_AMOUNT)))
            },
            Case("a bridge transfer to nobody", USDC.id, USDC_AMOUNT, provider = "funxyz") {
                listOf(env.evmPayload("pay", to = USDC_TOKEN, data = transferData("0x" + "0".repeat(40), USDC_AMOUNT)))
            },
            Case("a bridge transfer of another amount", USDC.id, USDC_AMOUNT, provider = "funxyz") {
                listOf(env.evmPayload("pay", to = USDC_TOKEN, data = transferData(BRIDGE_ADDRESS, USDC_AMOUNT + BigInteger.ONE)))
            },
            Case("a bridge transfer of another token", USDC.id, USDC_AMOUNT, provider = "funxyz") {
                listOf(env.evmPayload("pay", to = USDT_ETH_TOKEN, data = transferData(BRIDGE_ADDRESS, USDC_AMOUNT)))
            },
            Case("a bridge approval instead of a transfer", USDC.id, USDC_AMOUNT, provider = "funxyz") {
                listOf(env.evmPayload("pay", to = USDC_TOKEN, data = env.approveData(BRIDGE_ADDRESS, USDC_AMOUNT)))
            },
            Case("a second bridge transfer", USDC.id, USDC_AMOUNT, provider = "funxyz") {
                listOf(
                    env.evmPayload("pay", to = USDC_TOKEN, data = transferData(BRIDGE_ADDRESS, USDC_AMOUNT)),
                    env.evmPayload("more", to = USDC_TOKEN, data = transferData(BRIDGE_ADDRESS, USDC_AMOUNT)),
                )
            },
            Case("a bridge transfer that delegates", USDC.id, USDC_AMOUNT, provider = "funxyz") {
                listOf(env.evmPayload("pay", to = USDC_TOKEN, data = transferData(BRIDGE_ADDRESS, USDC_AMOUNT), authorizationNonce = EVM_NONCE + 1))
            },
            Case("a bridge deposit of a coin", bnb, BNB_AMOUNT, provider = "funxyz") {
                listOf(bscPayload("pay", to = BRIDGE_ADDRESS, value = BNB_AMOUNT))
            },
            Case("a bridge transfer of a token the bridge does not take", usdt, USDT_AMOUNT, provider = "funxyz") {
                listOf(bscPayload("pay", to = BSC_USDT_TOKEN, data = transferData(BRIDGE_ADDRESS, USDT_AMOUNT)))
            },
            Case("a Tron prebuilt transaction", trx, TRX_AMOUNT) { listOf(tronPrebuild()) },
            Case("a Tron deposit to a foreign contract", trx, TRX_AMOUNT) {
                listOf(env.tronPayload("pay", to = TestWallet.TRON_ADDRESS, data = env.tronNativeDepositData(), value = TRX_AMOUNT))
            },
            Case("a Tron deposit for another depositor", trx, TRX_AMOUNT) {
                listOf(env.tronPayload("pay", to = TRON_RELAY_DEPOSITORY, data = env.tronNativeDepositData(TRON_USDT), value = TRX_AMOUNT))
            },
            Case("a Tron deposit over the amount", trx, TRX_AMOUNT) {
                listOf(env.tronPayload("pay", to = TRON_RELAY_DEPOSITORY, data = env.tronNativeDepositData(), value = TRX_AMOUNT + BigInteger.ONE))
            },
            Case("a Tron deposit with a memo", trx, TRX_AMOUNT) { listOf(tronWithMemo()) },
            Case("a Tron token transfer instead of the deposit", tronUsdt, TRON_USDT_AMOUNT) {
                listOf(env.tronPayload("pay", to = TRON_USDT, data = "a9059cbb" + env.tronTokenDepositData(TRON_USDT, TRON_USDT_AMOUNT).drop(8 + 64 * 2)))
            },
            Case("a Tron approval to someone else", tronUsdt, TRON_USDT_AMOUNT) {
                listOf(
                    env.tronPayload("approve", to = TRON_USDT, data = env.tronApproveData(TestWallet.TRON_ADDRESS, TRON_USDT_AMOUNT)),
                    env.tronPayload("deposit", to = TRON_RELAY_DEPOSITORY, data = env.tronTokenDepositData(TRON_USDT, TRON_USDT_AMOUNT)),
                )
            },
            Case("a Tron approval over the amount", tronUsdt, TRON_USDT_AMOUNT) {
                listOf(
                    env.tronPayload("approve", to = TRON_USDT, data = env.tronApproveData(TRON_RELAY_DEPOSITORY, TRON_USDT_AMOUNT + BigInteger.ONE)),
                    env.tronPayload("deposit", to = TRON_RELAY_DEPOSITORY, data = env.tronTokenDepositData(TRON_USDT, TRON_USDT_AMOUNT)),
                )
            },
            Case("a Tron deposit through the gateway route", trx, TRX_AMOUNT, provider = "direct") {
                listOf(env.tronPayload("pay", to = TRON_RELAY_DEPOSITORY, data = env.tronNativeDepositData(), value = TRX_AMOUNT))
            },
            Case("a Tron deposit without Tron in the signed config", trx, TRX_AMOUNT, prepare = {
                it.servedSecurityBlock = it.securityBlock(relayDepositories = listOf("eth" to JsonPrimitive(RELAY_DEPOSITORY)))
            }) { listOf(env.tronPayload("pay", to = TRON_RELAY_DEPOSITORY, data = env.tronNativeDepositData(), value = TRX_AMOUNT)) },
            Case("a deposit without a verified config", usdt, USDT_AMOUNT, prepare = { it.servedSecurityBlock = null }) { listOf(bscBatch()) },
            Case("a token through Relay's router without an approval", usdt, USDT_AMOUNT) {
                listOf(bscPayload("pay", to = RELAY_ROUTER, data = proxied()))
            },
            Case("a token through Relay's receiver without an approval", usdt, USDT_AMOUNT) {
                listOf(bscPayload("pay", to = RELAY_RECEIVER, data = proxied()))
            },
            Case("a token through Relay's approval proxy approved to another contract", usdt, USDT_AMOUNT) {
                listOf(batch(approve(spender = RELAY_ROUTER), Triple(RELAY_APPROVAL_PROXY, BigInteger.ZERO, proxied())))
            },
            Case("a token through Relay's approval proxy approved for more", usdt, USDT_AMOUNT) {
                listOf(batch(approve(spender = RELAY_APPROVAL_PROXY, amount = USDT_AMOUNT + BigInteger.ONE), Triple(RELAY_APPROVAL_PROXY, BigInteger.ZERO, proxied())))
            },
            Case("a token through Relay's approval proxy transferring another token", usdt, USDT_AMOUNT) {
                listOf(bscPayload("pay", to = RELAY_APPROVAL_PROXY, data = proxied(tokens = listOf(OTHER))))
            },
            Case("a token through Relay's approval proxy transferring a second token", usdt, USDT_AMOUNT) {
                listOf(bscPayload("pay", to = RELAY_APPROVAL_PROXY, data = proxied(tokens = listOf(BSC_USDT_TOKEN, OTHER), amounts = listOf(USDT_AMOUNT, USDT_AMOUNT))))
            },
            Case("a token through Relay's approval proxy transferring it twice", usdt, USDT_AMOUNT) {
                val half = USDT_AMOUNT / BigInteger.TWO
                listOf(bscPayload("pay", to = RELAY_APPROVAL_PROXY, data = proxied(tokens = listOf(BSC_USDT_TOKEN, BSC_USDT_TOKEN), amounts = listOf(half, half))))
            },
            Case("a token through Relay's approval proxy transferring more", usdt, USDT_AMOUNT) {
                listOf(bscPayload("pay", to = RELAY_APPROVAL_PROXY, data = proxied(amounts = listOf(USDT_AMOUNT + BigInteger.ONE))))
            },
            Case("a token through Relay's approval proxy transferring well under the amount", usdt, USDT_AMOUNT) {
                listOf(bscPayload("pay", to = RELAY_APPROVAL_PROXY, data = proxied(amounts = listOf(USDT_AMOUNT * BigInteger.valueOf(96) / BigInteger.valueOf(100)))))
            },
            Case("a token through Relay's approval proxy transferring nothing", usdt, USDT_AMOUNT) {
                listOf(bscPayload("pay", to = RELAY_APPROVAL_PROXY, data = proxied(tokens = emptyList(), amounts = emptyList())))
            },
            Case("a token through Relay's approval proxy with an amount missing", usdt, USDT_AMOUNT) {
                listOf(bscPayload("pay", to = RELAY_APPROVAL_PROXY, data = proxied(amounts = emptyList())))
            },
            Case("a token through Relay's approval proxy with a token address that is not one", usdt, USDT_AMOUNT) {
                val dirty = proxied().replace("000000000000000000000000" + BSC_USDT_TOKEN.removePrefix("0x"), "ff0000000000000000000000" + BSC_USDT_TOKEN.removePrefix("0x"))
                listOf(bscPayload("pay", to = RELAY_APPROVAL_PROXY, data = dirty))
            },
            Case("a token through Relay's approval proxy with its arrays out of the call", usdt, USDT_AMOUNT) {
                listOf(bscPayload("pay", to = RELAY_APPROVAL_PROXY, data = proxied().take(10 + 64 * 7)))
            },
            Case("a token through Relay's approval proxy with an array at an odd offset", usdt, USDT_AMOUNT) {
                listOf(bscPayload("pay", to = RELAY_APPROVAL_PROXY, data = "0xf9e4bab4" + "c1".padStart(64, '0') + proxied().drop(10 + 64)))
            },
            Case("a token through Relay's approval proxy approved for less than it transfers", usdt, USDT_AMOUNT) {
                listOf(batch(approve(spender = RELAY_APPROVAL_PROXY, amount = USDT_AMOUNT - BigInteger.ONE), Triple(RELAY_APPROVAL_PROXY, BigInteger.ZERO, proxied())))
            },
            Case("a coin through Relay's approval proxy", bnb, BNB_AMOUNT) {
                listOf(bscPayload("pay", to = RELAY_APPROVAL_PROXY, value = BNB_AMOUNT, data = proxied(tokens = emptyList(), amounts = emptyList())))
            },
            Case("a coin through Relay's approval proxy that also pulls a token", bnb, BNB_AMOUNT) {
                listOf(bscPayload("pay", to = RELAY_APPROVAL_PROXY, value = BNB_AMOUNT, data = proxied()))
            },
            Case("a coin through Relay's receiver", bnb, BNB_AMOUNT) {
                listOf(bscPayload("pay", to = RELAY_RECEIVER, value = BNB_AMOUNT, data = ROUTER_CALL))
            },
            Case("a token through Relay's approval proxy signed for another network", usdt, USDT_AMOUNT, prepare = {
                it.servedSecurityBlock = it.signedSecurityBlock(securityPayload(routers = PerpsSecurityBlocks.relayRouters("eth")))
            }) { listOf(bscPayload("pay", to = RELAY_APPROVAL_PROXY, data = proxied())) },
            Case("a token through a contract the config signs as Relay's router, read as a router", usdt, USDT_AMOUNT, prepare = {
                it.servedSecurityBlock = it.signedSecurityBlock(
                    securityPayload(routers = listOf(PerpsSecurityBlocks.relayRouter("bsc", RELAY_APPROVAL_PROXY, "erc20_router"))),
                )
            }) { listOf(bscPayload("pay", to = RELAY_APPROVAL_PROXY, data = proxied())) },
            Case("another function of Relay's approval proxy", usdt, USDT_AMOUNT) {
                listOf(bscPayload("pay", to = RELAY_APPROVAL_PROXY, data = "0x12345678" + proxied().drop(10)))
            },
            Case("a coin through Relay's router for more", bnb, BNB_AMOUNT) {
                listOf(bscPayload("pay", to = RELAY_ROUTER, value = BNB_AMOUNT + BigInteger.ONE, data = ROUTER_CALL))
            },
            Case("a coin through Relay's router with a delegation elsewhere", bnb, BNB_AMOUNT) {
                listOf(bscPayload("pay", to = RELAY_ROUTER, value = BNB_AMOUNT, data = ROUTER_CALL, authorizationNonce = EVM_NONCE + 1, authorizationAddress = OTHER))
            },
        )
    }

    private fun quote(provider: String, payloads: (asked: JsonObject) -> List<JsonObject>) {
        env.quoteDeposits(provider = provider, credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID.takeIf { provider == "relay" }, payloads = payloads)
    }

    private suspend fun deposit(assetId: String, amount: BigInteger): ConfirmType.PerpetualDeposit {
        val request = env.depositService.confirmRequest(WALLET_ID, env.source(assetId), amount, PerpsSourceScreen.PerpsHome)
        return request.type as ConfirmType.PerpetualDeposit
    }

    private fun bscPayload(
        id: String,
        to: String,
        value: BigInteger = BigInteger.ZERO,
        data: String = "0x",
        authorizationNonce: Long? = null,
        authorizationAddress: String = HERMES_DELEGATE,
        authorizationChainId: Long = Chain.Smartchain.Mainnet.number,
    ): JsonObject = env.evmPayload(
        id = id,
        to = to,
        value = value,
        data = data,
        authorizationNonce = authorizationNonce,
        authorizationAddress = authorizationAddress,
        authorizationChainId = authorizationChainId,
        chain = "bsc/mainnet",
    )

    private fun bscBatch(): JsonObject = bscPayload(
        "batch",
        to = TestWallet.ETH_ADDRESS,
        data = env.batchData(
            Triple(BSC_USDT_TOKEN, BigInteger.ZERO, env.approveData(RELAY_DEPOSITORY, USDT_AMOUNT)),
            Triple(RELAY_DEPOSITORY, BigInteger.ZERO, env.tokenDepositData(BSC_USDT_TOKEN, USDT_AMOUNT)),
            chain = Chain.Smartchain.Mainnet,
        ),
        authorizationNonce = EVM_NONCE + 1,
    )

    private fun directBatch(
        approval: String = env.approveData(GATEWAY, USDC_AMOUNT),
        deposit: String = env.gatewayDepositData(L1_ADDRESS, USDC_AMOUNT),
        extra: Triple<String, BigInteger, String>? = null,
        authorizationNonce: Long? = null,
    ): JsonObject {
        val calls = listOfNotNull(
            Triple(USDC_TOKEN, BigInteger.ZERO, approval),
            Triple(GATEWAY, BigInteger.ZERO, deposit),
            extra,
        )
        return env.evmPayload("batch", to = TestWallet.ETH_ADDRESS, data = env.batchData(*calls.toTypedArray()), authorizationNonce = authorizationNonce)
    }

    private fun tronPrebuild(): JsonObject = buildJsonObject {
        put("id", "pay")
        put("type", "prebuild")
        put("chain_id", "tron/mainnet")
        put("version", 1)
        putJsonObject("meta") { put("data", "{\"txID\":\"00\"}") }
    }

    private fun tronWithMemo(): JsonObject = buildJsonObject {
        put("id", "pay")
        put("type", "main")
        put("chain_id", "tron/mainnet")
        put("version", 1)
        putJsonObject("meta") {
            put("to", TRON_RELAY_DEPOSITORY)
            put("value", TRX_AMOUNT.toString())
            put("data", env.tronNativeDepositData())
            put("memo", "deposit")
        }
    }

    private fun routedTokenPayment(proxy: String, amount: BigInteger): JsonObject = env.evmPayload(
        "pay",
        to = TestWallet.ETH_ADDRESS,
        data = env.batchData(
            Triple(USDT_ETH_TOKEN, BigInteger.ZERO, env.approveData(proxy, amount)),
            Triple(proxy, BigInteger.ZERO, proxyCallData(listOf(USDT_ETH_TOKEN), listOf(amount))),
        ),
    )

    private fun relayCoinPayments(): List<Pair<String, String>> = listOf(
        RELAY_DEPOSITORY to env.nativeDepositData(),
        RELAY_ROUTER to ROUTER_CALL,
    )

    private fun proxied(
        tokens: List<String> = listOf(BSC_USDT_TOKEN),
        amounts: List<BigInteger> = listOf(USDT_AMOUNT),
    ): String = proxyCallData(tokens, amounts)

    private fun proxyCallData(tokens: List<String>, amounts: List<BigInteger>): String {
        val headBytes = 6 * 32
        val tokensOffset = headBytes
        val amountsOffset = tokensOffset + (tokens.size + 1) * 32
        val callsOffset = amountsOffset + (amounts.size + 1) * 32
        val metadataOffset = callsOffset + 32
        val head = listOf(
            word(tokensOffset.toBigInteger()),
            word(amountsOffset.toBigInteger()),
            word(callsOffset.toBigInteger()),
            word(L1_ADDRESS),
            word(L1_ADDRESS),
            word(metadataOffset.toBigInteger()),
        )
        val tail = listOf(word(tokens.size.toBigInteger())) + tokens.map(::word) +
            word(amounts.size.toBigInteger()) + amounts.map(::word) +
            word(BigInteger.ZERO) +
            word(BigInteger.ZERO)
        return "0xf9e4bab4" + (head + tail).joinToString("")
    }

    private fun word(address: String): String = address.lowercase().removePrefix("0x").padStart(64, '0')

    private fun word(value: BigInteger): String = value.toString(16).padStart(64, '0')

    private fun securityPayload(
        gateway: Boolean = true,
        chains: List<String> = listOf("eth", "bsc"),
        routers: List<JsonObject> = PerpsSecurityBlocks.relayRouters(*chains.toTypedArray()),
    ): JsonObject {
        val payload = PerpsSecurityBlocks.payload(
            delegateChains = listOf("eth", "arb", "bsc"),
            relayDepositories = chains.map { it to JsonPrimitive(RELAY_DEPOSITORY) } + ("tron" to JsonPrimitive(TRON_RELAY_DEPOSITORY)),
            relayRouters = routers,
        )
        val contracts = payload.getValue("contracts") as JsonObject
        val trimmed = if (gateway) {
            contracts
        } else {
            JsonObject(contracts - "lighter_gateway")
        }
        return JsonObject(payload + ("contracts" to trimmed))
    }

    private fun transferData(to: String, amount: BigInteger): String {
        return "0xa9059cbb" + to.lowercase().removePrefix("0x").padStart(64, '0') + amount.toString(16).padStart(64, '0')
    }

    private suspend fun failureOf(block: suspend () -> Unit): Throwable {
        val failure = try {
            block()
            null
        } catch (e: Throwable) {
            e
        }
        return checkNotNull(failure) { "expected the call to fail" }
    }

    private fun JsonObject.registersKey(): Boolean = get("change_pub_key")?.let { it !is JsonNull } == true

    private companion object {
        const val BSC_USDT_TOKEN = "0x55d398326f99059ff775485246999027b3197955"
        const val USDT_ETH_TOKEN = "0xdac17f958d2ee523a2206206994597c13d831ec7"
        val BSC_USDT = testAsset("bsc/mainnet/bep20/$BSC_USDT_TOKEN", "USDT", "BNB Smart Chain", decimals = 18)
        val BNB = testAsset("bsc/mainnet/coin", "BNB", "BNB Smart Chain", decimals = 18)
        const val TRON_USDT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        const val OTHER = "0x45a9034c5dc4e1731b7ff8f977e50012822d5b9d"
        const val BRIDGE_ADDRESS = "0x812e3c34e39658927fa0b30685ffa79a8e11884f"
        const val RELAY_ROUTER = "0x0729e2a4716d9f7dd17858d5d504426d228697f7"
        const val RELAY_APPROVAL_PROXY = "0x5567954cd026cebfe486062cb75848e0f1c12e58"
        const val RELAY_RECEIVER = "0xa5f565650890fba1824ee0f21ebbbf660a179934"
        const val OTHER_ROUTER = "0x9a6f2c3e1b7d4a8c5e0f1b2d3c4a5e6f7a8b9c0d"
        const val ROUTER_CALL = "0xf9e4bab4"
        const val AVALANCHE_USDT_TOKEN = "0x9702230a8ea53601f5cd2dc00fdbc13d4df4a8c7"
        val AVALANCHE_USDT = testAsset("avax/mainnet/erc20/$AVALANCHE_USDT_TOKEN", "USDT", "Avalanche", decimals = 6)
        val AVALANCHE_USDT_AMOUNT: BigInteger = BigInteger.valueOf(100_000_000L)
        const val AVALANCHE_PROXY_CALL = "relay_approval_proxy_usdt_avalanche_2026_11_13.txt"
        val LARGE_BNB_BUDGET: BigInteger = BigInteger("12000000000000000000")
        val LARGE_FIRST_BNB_DEPOSIT: BigInteger = BigInteger("11879928271503401734")
        val USDT_AMOUNT: BigInteger = BigInteger("3000000000000000000")
        val BNB_AMOUNT: BigInteger = BigInteger("3000000000000000")
        val ETH_AMOUNT: BigInteger = BigInteger("10000000000000000")
        val ETH_BUDGET: BigInteger = BigInteger("1000000000000000")
        val VALUE_REFUSED = PerpsQuoteError.UnexpectedOperation("chain payment value")
        val USDT_ETH_AMOUNT: BigInteger = BigInteger.valueOf(3_000_000L)
        val TRX_AMOUNT: BigInteger = BigInteger.valueOf(3_464_318L)
        val TRON_USDT_AMOUNT: BigInteger = BigInteger.valueOf(5_000_000L)
        val TRON_QUOTED_FEE: BigInteger = BigInteger.valueOf(11_233_560L)
        val TRON_BANDWIDTH_FEE: BigInteger = BigInteger.valueOf(345_000L)
        val USDC_AMOUNT: BigInteger = BigInteger.valueOf(6_000_000L)
        val CREDITED: BigInteger = BigInteger.valueOf(2_935_206L)
    }
}
