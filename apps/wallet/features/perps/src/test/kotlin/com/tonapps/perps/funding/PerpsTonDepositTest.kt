package com.tonapps.perps.funding

import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.generated.Events.Perps.PerpsSourceScreen
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.QuoteChangedException
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.AGGREGATOR_ACCOUNT_INDEX
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.DEPOSIT_EXECUTION_ID
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GET_ACCOUNT
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.HTTP_UNAVAILABLE
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.QUOTE_DEPOSIT
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.RELAY_DEPOSITORY
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.RELAY_ORDER_ID
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.WALLET_ID
import com.tonapps.perps.security.PerpsSecurityBlocks
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

class PerpsTonDepositTest : BaseUnitTest() {

    private lateinit var env: PerpsFundingTestEnv

    private val refused: List<Pair<String, () -> List<JsonObject>>> = listOf(
        "foreign address" to { listOf(env.tonPayload("pay", to = TestWallet.TON_ADDRESS, value = TON_AMOUNT)) },
        "the depository's hash on the masterchain" to {
            listOf(env.tonPayload("pay", to = "-1:" + PerpsSecurityBlocks.RELAY_TON_DEPOSITORY.substringAfter(':'), value = TON_AMOUNT))
        },
        "unparsable address" to { listOf(env.tonPayload("pay", to = "relay", value = TON_AMOUNT)) },
        "a second message" to {
            listOf(env.tonPayload("pay", value = TON_AMOUNT), env.tonPayload("more", to = TestWallet.TON_ADDRESS, value = TON_AMOUNT))
        },
        "the order twice" to { listOf(env.tonPayload("pay", value = TON_AMOUNT), env.tonPayload("again", value = TON_AMOUNT)) },
        "well below the budget" to { listOf(env.tonPayload("pay", value = TON_AMOUNT * BigInteger.valueOf(96) / BigInteger.valueOf(100))) },
        "more than entered" to { listOf(env.tonPayload("pay", value = TON_AMOUNT + BigInteger.ONE)) },
        "nothing" to { listOf(env.tonPayload("pay", value = BigInteger.ZERO)) },
        "jetton transfer body" to {
            listOf(env.tonPayload("pay", value = TON_AMOUNT, body = env.tonBody(op = JETTON_TRANSFER_OP) { storeUInt(0, QUERY_ID_BITS) }))
        },
        "another op" to { listOf(env.tonPayload("pay", value = TON_AMOUNT, body = env.tonBody(op = 1) { storeBytes(RELAY_ORDER_ID.toByteArray()) })) },
        "a comment with a ref" to {
            listOf(env.tonPayload("pay", value = TON_AMOUNT, body = env.tonBody(op = 0, refs = 1) { storeBytes(RELAY_ORDER_ID.toByteArray()) }))
        },
        "a comment that is not the order" to { listOf(env.tonPayload("pay", value = TON_AMOUNT, body = env.tonComment("deposit"))) },
        "a short order" to { listOf(env.tonPayload("pay", value = TON_AMOUNT, body = env.tonComment(RELAY_ORDER_ID.dropLast(2)))) },
        "an order with more" to { listOf(env.tonPayload("pay", value = TON_AMOUNT, body = env.tonComment("$RELAY_ORDER_ID "))) },
        "a comment off by bits" to {
            listOf(env.tonPayload("pay", value = TON_AMOUNT, body = env.tonBody(op = 0) { storeBytes(RELAY_ORDER_ID.toByteArray()); storeUInt(1, 1) }))
        },
        "a body that is no BOC" to { listOf(env.tonPayload("pay", value = TON_AMOUNT, body = "te6cc")) },
        "no comment" to { listOf(env.tonPayload("pay", value = TON_AMOUNT, body = null)) },
        "body and comment" to { listOf(env.tonPayload("pay", value = TON_AMOUNT, meta = comment(RELAY_ORDER_ID))) },
        "a plain comment that is not the order" to { listOf(env.tonPayload("pay", value = TON_AMOUNT, body = null, meta = comment("deposit"))) },
        "jetton deposit" to {
            listOf(env.tonPayload("pay", value = TON_AMOUNT, body = null, meta = buildJsonObject { put("token", USDT_TON_MASTER) }))
        },
        "state init" to { listOf(env.tonPayload("pay", value = TON_AMOUNT, meta = buildJsonObject { put("state_init", env.tonComment("")) })) },
        "mode 128" to { listOf(env.tonPayload("pay", value = TON_AMOUNT, mode = 128)) },
        "mode 130" to { listOf(env.tonPayload("pay", value = TON_AMOUNT, mode = 130)) },
        "mode 64" to { listOf(env.tonPayload("pay", value = TON_AMOUNT, mode = 64)) },
        "mode 35" to { listOf(env.tonPayload("pay", value = TON_AMOUNT, mode = 35)) },
        "mode 16" to { listOf(env.tonPayload("pay", value = TON_AMOUNT, mode = 16)) },
        "negative mode" to { listOf(env.tonPayload("pay", value = TON_AMOUNT, mode = -1)) },
        "batch" to {
            listOf(
                buildJsonObject {
                    put("id", "pay")
                    put("type", "batch")
                    put("chain_id", "ton/mainnet")
                    put("version", 1)
                    put(
                        "meta",
                        buildJsonObject {
                            put("calls", buildJsonArray { add(env.tonPayload("call", value = TON_AMOUNT).withType("main")) })
                        },
                    )
                },
            )
        },
        "prebuild" to {
            listOf(
                buildJsonObject {
                    put("id", "pay")
                    put("type", "prebuild")
                    put("chain_id", "ton/mainnet")
                    put("version", 1)
                    put(
                        "meta",
                        buildJsonObject {
                            put("format", "int_msg")
                            put("data", env.tonComment(RELAY_ORDER_ID))
                        },
                    )
                },
            )
        },
        "another chain" to { listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = TON_AMOUNT)) },
    )

    @Before
    fun setUp() {
        env = PerpsFundingTestEnv()
        env.accounts[TestAssets.ton.id] = testAccount(TestAssets.ton, balance = TON_BALANCE.toString())
    }

    @Test
    fun aDepositFromTonOpensAsTheOneMessageToRelaysDepository() = runTest {
        quoteTonDeposits { listOf(env.tonPayload("pay", value = TON_AMOUNT)) }

        val request = env.depositService.confirmRequest(WALLET_ID, tonSource(), TON_AMOUNT, PerpsSourceScreen.PerpsHome)

        assertEquals(listOf(GET_ACCOUNT, QUOTE_DEPOSIT), env.endpoints)
        val deposit = request.type as ConfirmType.PerpetualDeposit
        val payload = deposit.operation.payloads.single()
        assertEquals(Chain.Ton.Mainnet, payload.chain)
        val message = payload.meta as PayloadMeta.Ton.Main
        assertEquals(PerpsSecurityBlocks.RELAY_TON_DEPOSITORY_FRIENDLY, message.to.display)
        assertEquals(TON_AMOUNT.toString(), message.value.toString())
        assertEquals(TON_AMOUNT.toString(), deposit.amount.toString())
        assertEquals("Relay", deposit.verifiedPayment?.provider)
        assertEquals(TON_AMOUNT.toString(), deposit.verifiedPayment?.amount.toString())
    }

    @Test
    fun theAmountEnteredIsABudgetTheMessageMayCarryLessOf() = runTest {
        val sent = TON_AMOUNT - QUOTED_NETWORK_FEE
        quoteTonDeposits { listOf(env.tonPayload("pay", value = sent)) }

        val request = env.depositService.confirmRequest(WALLET_ID, tonSource(), TON_AMOUNT, PerpsSourceScreen.PerpsHome)

        val deposit = request.type as ConfirmType.PerpetualDeposit
        assertEquals(TON_AMOUNT.toString(), deposit.amount.toString())
        assertEquals(sent.toString(), deposit.verifiedPayment?.amount.toString())
    }

    @Test
    fun theDepositoryMayComeInAnyFormOfItsAddressAndTheOrderAsAPlainComment() = runTest {
        val accepted = listOf(
            env.tonPayload("raw", to = PerpsSecurityBlocks.RELAY_TON_DEPOSITORY, value = TON_AMOUNT),
            env.tonPayload("non-bounceable", to = NON_BOUNCEABLE_DEPOSITORY, value = TON_AMOUNT),
            env.tonPayload("comment", value = TON_AMOUNT, body = null, meta = comment(RELAY_ORDER_ID)),
            env.tonPayload("empty comment", value = TON_AMOUNT, meta = comment("")),
            env.tonPayload("upper-case order", value = TON_AMOUNT, body = env.tonComment(RELAY_ORDER_ID.uppercase().replace("0X", "0x"))),
        ) + listOf(null, 0, 1, 2, 3).map { mode -> env.tonPayload("mode $mode", value = TON_AMOUNT, mode = mode) }

        accepted.forEach { payload ->
            quoteTonDeposits { listOf(payload) }

            val request = env.depositService.confirmRequest(WALLET_ID, tonSource(), TON_AMOUNT, PerpsSourceScreen.PerpsHome)

            val deposit = request.type as ConfirmType.PerpetualDeposit
            assertEquals("$payload", payload.string("id"), deposit.operation.payloads.single().id)
        }
    }

    @Test
    fun anyOtherMessageFromTonIsNoQuote() = runTest {
        val opened = refused.mapNotNull { (name, payloads) ->
            quoteTonDeposits { payloads() }

            val failure = try {
                env.depositService.confirmRequest(WALLET_ID, tonSource(), TON_AMOUNT, PerpsSourceScreen.PerpsHome)
                null
            } catch (e: PerpsDepositException.QuoteRejected) {
                e
            }

            name.takeIf { failure?.toDepositQuoteError() != PerpsDepositQuoteError.QuoteFailed }
        }

        assertEquals(emptyList<String>(), opened)
        assertTrue(env.journal.list(WALLET_ID).isEmpty())
    }

    @Test
    fun aDepositFromTonIsNoQuoteWithoutTonInTheSignedConfigAndOtherDepositsStillOpen() = runTest {
        val evmDepository = "eth" to JsonPrimitive(RELAY_DEPOSITORY)
        val withoutTon = listOf(
            env.securityBlock(relayDepositories = listOf("lighter" to JsonPrimitive(AGGREGATOR_ACCOUNT_INDEX.toString()), evmDepository)),
            env.securityBlock(relayDepositories = listOf("ton" to JsonPrimitive("relay"), evmDepository)),
            env.securityBlock(relayDepositories = listOf("eth" to JsonPrimitive(PerpsSecurityBlocks.RELAY_TON_DEPOSITORY), evmDepository)),
        )
        quoteTonDeposits { asked ->
            if (asked.string("source_asset") == TestAssets.ton.id) {
                listOf(env.tonPayload("pay", value = TON_AMOUNT))
            } else {
                listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData()))
            }
        }

        withoutTon.forEach { block ->
            env.servedSecurityBlock = block

            val failure = failureOf { env.depositService.confirmRequest(WALLET_ID, tonSource(), TON_AMOUNT, PerpsSourceScreen.PerpsHome) }
            assertTrue("$block: $failure", failure is PerpsDepositException.QuoteRejected)

            val evm = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
            val evmDeposit = evm.type as ConfirmType.PerpetualDeposit
            assertEquals("pay", evmDeposit.operation.payloads.single().id)
            assertEquals(ETH_AMOUNT.toString(), evmDeposit.verifiedPayment?.amount.toString())
        }
    }

    @Test
    fun aDepositFromTonIsNoQuoteWithoutAVerifiedConfig() = runTest {
        env.servedSecurityBlock = null
        quoteTonDeposits { listOf(env.tonPayload("pay", value = TON_AMOUNT)) }

        val failure = failureOf { env.depositService.confirmRequest(WALLET_ID, tonSource(), TON_AMOUNT, PerpsSourceScreen.PerpsHome) }

        assertTrue("$failure", failure is PerpsDepositException.QuoteRejected)
    }

    @Test
    fun aFirstDepositFromTonChecksThePaymentItIsPricedOn() = runTest {
        env.withoutAccount()
        quoteTonDeposits { asked ->
            if (asked.registersKey()) {
                listOf(env.authorizationPayload("authorization"), env.hermesPayload("execute"))
            } else {
                listOf(env.tonPayload("pay", to = TestWallet.TON_ADDRESS, value = TON_AMOUNT))
            }
        }

        val failure = failureOf { env.depositService.confirmRequest(WALLET_ID, tonSource(), TON_AMOUNT, PerpsSourceScreen.PerpsHome) }

        assertTrue("$failure", failure is PerpsDepositException.QuoteRejected)
    }

    @Test
    fun aFirstDepositFromTonQuotedAgainOnTheSwipeIsHeldToTheSameRules() = runTest {
        signPreviews()
        env.withoutAccount()
        var paidTo = PerpsSecurityBlocks.RELAY_TON_DEPOSITORY_FRIENDLY
        var paidValue = percentOfBudget(99)
        quoteTonDeposits { asked ->
            if (asked.registersKey() && "operation" !in asked) {
                listOf(env.authorizationPayload("authorization"), env.hermesPayload("execute"))
            } else {
                listOf(env.tonPayload("pay", to = paidTo, value = paidValue))
            }
        }
        val previewed = env.depositService.confirmRequest(WALLET_ID, tonSource(), TON_AMOUNT, PerpsSourceScreen.PerpsHome)
        assertTrue(previewed.data.preview)
        val preview = previewed.type as ConfirmType.PerpetualDeposit
        assertEquals(listOf("pay"), preview.paymentOperation?.payloads?.map { it.id })
        assertEquals("Relay", preview.verifiedPayment?.provider)
        assertEquals(paidValue.toString(), preview.verifiedPayment?.amount.toString())

        paidValue = TON_AMOUNT * BigInteger.valueOf(995) / BigInteger.valueOf(1000)
        val authorized = env.depositService.authorize(previewed, mockk<MnemonicWallet>())
        assertFalse(authorized.data.preview)
        val deposit = authorized.type as ConfirmType.PerpetualDeposit
        assertEquals("pay", deposit.operation.payloads.single().id)
        assertEquals("Relay", deposit.verifiedPayment?.provider)

        paidValue = TON_AMOUNT
        val full = env.depositService.authorize(previewed, mockk<MnemonicWallet>())
        assertEquals(paidValue.toString(), (full.type as ConfirmType.PerpetualDeposit).verifiedPayment?.amount.toString())

        paidValue = percentOfBudget(97)
        val lowest = env.depositService.authorize(previewed, mockk<MnemonicWallet>())
        assertEquals(paidValue.toString(), (lowest.type as ConfirmType.PerpetualDeposit).verifiedPayment?.amount.toString())

        paidValue = percentOfBudget(96)
        val short = failureOf { env.depositService.authorize(previewed, mockk<MnemonicWallet>()) }
        assertTrue("$short", short is PerpsDepositException.QuoteRejected)

        paidValue = TON_AMOUNT
        paidTo = TestWallet.TON_ADDRESS
        val failure = failureOf { env.depositService.authorize(previewed, mockk<MnemonicWallet>()) }

        assertTrue("$failure", failure is PerpsDepositException.QuoteRejected)
        coVerify(exactly = 0) { env.provider.signPayload(any(), match { it.chain == Chain.Ton.Mainnet }, any(), any(), any()) }
    }

    @Test
    fun aFirstDepositFromTonShownWithoutItsPaymentIsShownAgainOnceThePaymentIsQuoted() = runTest {
        signPreviews()
        env.withoutAccount()
        env.quoteDeposits(
            provider = "relay",
            credited = CREDITED,
            executionId = DEPOSIT_EXECUTION_ID,
            refusal = { asked ->
                if (!asked.registersKey()) {
                    env.refused(HTTP_UNAVAILABLE, code = "unavailable", reason = null, retryable = true)
                } else {
                    null
                }
            },
        ) { asked ->
            if ("operation" !in asked) {
                listOf(env.authorizationPayload("authorization"), env.hermesPayload("execute"))
            } else {
                listOf(env.tonPayload("pay", value = TON_AMOUNT))
            }
        }
        val previewed = env.depositService.confirmRequest(WALLET_ID, tonSource(), TON_AMOUNT, PerpsSourceScreen.PerpsHome)
        assertEquals(null, (previewed.type as ConfirmType.PerpetualDeposit).verifiedPayment)

        val changed = failureOf { env.depositService.authorize(previewed, mockk<MnemonicWallet>()) }

        assertTrue("$changed", changed is QuoteChangedException)
        val requoted = (changed as QuoteChangedException).request
        assertFalse(requoted.data.preview)
        val payment = (requoted.type as ConfirmType.PerpetualDeposit).verifiedPayment
        assertEquals("Relay", payment?.provider)
        assertEquals(TON_AMOUNT.toString(), payment?.amount.toString())
    }

    private fun percentOfBudget(percent: Long): BigInteger = TON_AMOUNT * BigInteger.valueOf(percent) / BigInteger.valueOf(100)

    private fun JsonObject.withType(type: String): JsonObject = buildJsonObject {
        put("type", type)
        put("meta", getValue("meta"))
    }

    private fun comment(text: String): JsonObject = buildJsonObject { put("comment", text) }

    private fun quoteTonDeposits(payloads: (asked: JsonObject) -> List<JsonObject>) {
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID, payloads = payloads)
    }

    private fun tonSource(): PerpsDepositSource = env.source(TestAssets.ton.id)

    private fun JsonObject.registersKey(): Boolean = get("change_pub_key")?.let { it !is JsonNull } == true

    private fun signPreviews() {
        coEvery { env.provider.signPayload(any(), any(), any(), any(), any()) } answers {
            val payload = firstArg<Payload>()
            val data = when (payload.type) {
                Payload.Type.Eip7702Authorization -> buildJsonObject {
                    put("y_parity", 1)
                    put("r", "0x" + "ab".repeat(32))
                    put("s", "0x" + "cd".repeat(32))
                }
                Payload.Type.Eip7821HermesDelegate -> JsonPrimitive("0x" + "ef".repeat(64) + "1b")
                else -> error("unexpected ${payload.type}")
            }
            SignRes.ok(SignedPayload(payload.id, SignedPayload.Type.of(payload.type), payload.chain, data))
        }
    }

    private suspend fun failureOf(block: suspend () -> Unit): Throwable {
        val failure = try {
            block()
            null
        } catch (e: Throwable) {
            e
        }
        assertNotNull("expected the call to fail", failure)
        return checkNotNull(failure)
    }

    private companion object {
        val TON_AMOUNT: BigInteger = BigInteger.valueOf(3_000_000_000L)
        val TON_BALANCE: BigInteger = BigInteger.valueOf(10_000_000_000L)
        val ETH_AMOUNT: BigInteger = BigInteger("10000000000000000")
        val CREDITED: BigInteger = BigInteger.valueOf(2_000_000L)
        val QUOTED_NETWORK_FEE: BigInteger = BigInteger.valueOf(1_789_731L)
        const val NON_BOUNCEABLE_DEPOSITORY = "UQCrdGsDTqA2t6xRR4N6V4J705F7w_VQbUdHnofsh-8lVN4p"
        const val USDT_TON_MASTER = "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"
        const val JETTON_TRANSFER_OP = 0x0f8a7ea5
        const val QUERY_ID_BITS = 64
    }
}
