package com.tonapps.perps.funding

import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.generated.Events.Perps.PerpsSourceScreen
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.ChainError
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.QuoteChangedException
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.AGGREGATOR_ACCOUNT_INDEX
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.DEPOSIT_EXECUTION_ID
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GET_ACCOUNT
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.QUOTE_DEPOSIT
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.WALLET_ID
import com.tonapps.perps.funding.TestPsbt.DEPOSITORY_SCRIPT
import com.tonapps.perps.funding.TestPsbt.FEE
import com.tonapps.perps.funding.TestPsbt.FOREIGN_SCRIPT
import com.tonapps.perps.funding.TestPsbt.FOREIGN_TAPROOT_SCRIPT
import com.tonapps.perps.funding.TestPsbt.Input
import com.tonapps.perps.funding.TestPsbt.ORDER_ID
import com.tonapps.perps.funding.TestPsbt.ORDER_SCRIPT
import com.tonapps.perps.funding.TestPsbt.Output
import com.tonapps.perps.funding.TestPsbt.SENDER_SCRIPT
import com.tonapps.perps.security.PerpsSecurityBlocks
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.testAccount
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

class PerpsBtcDepositTest : BaseUnitTest() {

    private lateinit var env: PerpsFundingTestEnv

    private val refused: List<Pair<String, () -> List<JsonObject>>> = listOf(
        "a foreign recipient" to {
            psbt(outputs = listOf(Output(FOREIGN_SCRIPT, AMOUNT), change(), order()))
        },
        "a foreign taproot recipient" to {
            psbt(outputs = listOf(Output(FOREIGN_TAPROOT_SCRIPT, AMOUNT), change(), order()))
        },
        "an extra output" to {
            psbt(outputs = listOf(deposit(), change(), order(), Output(FOREIGN_SCRIPT, 546)))
        },
        "the depository twice" to {
            psbt(outputs = listOf(deposit(AMOUNT / 2), deposit(AMOUNT / 2), change(), order()))
        },
        "change to another wallet" to {
            psbt(outputs = listOf(deposit(), Output(FOREIGN_SCRIPT, CHANGE), order()))
        },
        "change twice" to {
            psbt(outputs = listOf(deposit(), change(CHANGE / 2), change(CHANGE / 2), order()))
        },
        "an OP_RETURN that carries sats" to {
            psbt(outputs = listOf(deposit(), change(), Output(ORDER_SCRIPT, 546)))
        },
        "no OP_RETURN" to { psbt(outputs = listOf(deposit(), change())) },
        "two OP_RETURNs" to { psbt(outputs = listOf(deposit(), change(), order(), order())) },
        "an OP_RETURN that is not the order" to {
            psbt(outputs = listOf(deposit(), change(), Output(TestPsbt.opReturn("deposit"), 0)))
        },
        "an OP_RETURN with more than the order" to {
            psbt(outputs = listOf(deposit(), change(), Output(TestPsbt.opReturn("$ORDER_ID "), 0)))
        },
        "an OP_RETURN of two pushes" to {
            psbt(outputs = listOf(deposit(), change(), Output(ORDER_SCRIPT + "00", 0)))
        },
        "more than entered" to { psbt(outputs = listOf(deposit(AMOUNT + 1), change(), order())) },
        "well below the budget" to { psbt(outputs = listOf(deposit(AMOUNT * 96 / 100), change(), order())) },
        "nothing to the depository" to { psbt(outputs = listOf(deposit(0), change(), order())) },
        "another wallet's coin" to {
            psbt(inputs = listOf(Input(SENDER_SCRIPT, IN), Input(FOREIGN_SCRIPT, 10_000)))
        },
        "a coin it carries no UTXO for" to {
            psbt(inputs = listOf(Input(SENDER_SCRIPT, IN), Input(null, 10_000)))
        },
        "SIGHASH_NONE" to { psbt(inputs = listOf(Input(SENDER_SCRIPT, IN, sighash = 2))) },
        "SIGHASH_SINGLE" to { psbt(inputs = listOf(Input(SENDER_SCRIPT, IN, sighash = 3))) },
        "SIGHASH_ALL|ANYONECANPAY" to { psbt(inputs = listOf(Input(SENDER_SCRIPT, IN, sighash = 0x81))) },
        "SIGHASH_DEFAULT on a SegWit v0 coin" to { psbt(inputs = listOf(Input(SENDER_SCRIPT, IN, sighash = 0))) },
        "outputs past the inputs" to { psbt(inputs = listOf(Input(SENDER_SCRIPT, AMOUNT + CHANGE - 1))) },
        "the balance to the miners" to { psbt(inputs = listOf(Input(SENDER_SCRIPT, IN), Input(SENDER_SCRIPT, 1_000_000))) },
        "a payment to the depository" to {
            listOf(
                buildJsonObject {
                    put("id", "pay")
                    put("type", "main")
                    put("chain_id", "btc/mainnet")
                    put("version", 1)
                    putJsonObject("meta") {
                        put("to", PerpsSecurityBlocks.RELAY_BTC_DEPOSITORY)
                        put("value", AMOUNT.toString())
                        put("memo", ORDER_ID)
                    }
                },
            )
        },
        "two PSBTs" to { psbt() + psbt(id = "again") },
        "no PSBT" to { listOf(env.bitcoinPayload("pay", psbt = "cHNidP8BAA==")) },
        "another chain" to { listOf(env.tonPayload("pay", value = BigInteger.valueOf(AMOUNT))) },
    )

    @Before
    fun setUp() {
        env = PerpsFundingTestEnv()
        env.accounts[TestAssets.btc.id] = testAccount(TestAssets.btc, balance = BALANCE.toString())
        env.bitcoinFeeRate(WALLET_FEE_RATE)
        coEvery { env.provider.estimateFee(match<Payload> { it.meta is PayloadMeta.Bitcoin.Prebuild }, any(), any()) } answers {
            ChainRes.ok(checkNotNull(firstArg<Payload>().fee))
        }
    }

    @Test
    fun aDepositFromBitcoinOpensAsThePsbtToRelaysDepositoryAtTheFeeItPays() = runTest {
        quoteBtcDeposits { psbt() }

        val request = env.depositService.confirmRequest(WALLET_ID, btcSource(), BigInteger.valueOf(AMOUNT), PerpsSourceScreen.PerpsHome)

        assertEquals(listOf(GET_ACCOUNT, QUOTE_DEPOSIT), env.endpoints)
        val deposit = request.type as ConfirmType.PerpetualDeposit
        val payload = deposit.operation.payloads.single()
        assertEquals("pay", payload.id)
        assertTrue(payload.meta is PayloadMeta.Bitcoin.Prebuild)
        assertEquals(FEE.toString(), (payload.fee as Fee.Value).amount.toString())
        assertEquals(Chain.Bitcoin.Mainnet.toAsset().id, payload.fee?.asset?.id)
        assertEquals(AMOUNT.toString(), deposit.amount.toString())
        assertEquals("Relay", deposit.verifiedPayment?.provider)
        assertEquals(AMOUNT.toString(), deposit.verifiedPayment?.amount.toString())
    }

    @Test
    fun theAmountEnteredIsABudgetTheDepositoryMayGetUpToThreePercentLessOf() = runTest {
        val sent = AMOUNT - 7
        quoteBtcDeposits { psbt(inputs = listOf(Input(SENDER_SCRIPT, IN - 7)), outputs = listOf(deposit(sent), change(), order())) }

        val request = env.depositService.confirmRequest(WALLET_ID, btcSource(), BigInteger.valueOf(AMOUNT), PerpsSourceScreen.PerpsHome)

        val deposit = request.type as ConfirmType.PerpetualDeposit
        assertEquals(AMOUNT.toString(), deposit.amount.toString())
        assertEquals(sent.toString(), deposit.verifiedPayment?.amount.toString())
    }

    @Test
    fun theFeeShownIsWhatThePsbtPaysNotWhatTheQuoteSays() = runTest {
        quoteBtcDeposits(quotedFee = 1_140) { psbt() }

        val quote = env.depositService.quote(WALLET_ID, btcSource(), BigInteger.valueOf(AMOUNT), PerpsSourceScreen.PerpsHome)

        val payload = (quote.request.type as ConfirmType.PerpetualDeposit).operation.payloads.single()
        assertEquals(FEE.toString(), (payload.fee as Fee.Value).amount.toString())
        coVerify { env.provider.estimateFee(match<Payload> { (it.fee as? Fee.Value)?.amount?.toString() == FEE.toString() }, any(), any()) }
    }

    @Test
    fun theWalletsOwnCoinsWithNoChangeOrManyCoinsAndAnyWayOfSigningEveryOutputAreADeposit() = runTest {
        val accepted = listOf(
            "no change" to psbt(inputs = listOf(Input(SENDER_SCRIPT, AMOUNT + FEE)), outputs = listOf(deposit(), order())),
            "97% of the budget" to psbt(outputs = listOf(deposit(AMOUNT * 97 / 100), change(), order())),
            "two coins" to psbt(inputs = listOf(Input(SENDER_SCRIPT, 3_000), Input(SENDER_SCRIPT, IN - 3_000))),
            "SIGHASH_ALL" to psbt(inputs = listOf(Input(SENDER_SCRIPT, IN, sighash = 1))),
            "outputs in another order" to psbt(outputs = listOf(order(), change(), deposit())),
            "an order pushed with PUSHDATA1" to psbt(outputs = listOf(deposit(), change(), Output("6a4c42" + ORDER_SCRIPT.drop(4), 0))),
            "an upper-case order" to psbt(
                outputs = listOf(deposit(), change(), Output(TestPsbt.opReturn("0x" + ORDER_ID.drop(2).uppercase()), 0)),
            ),
        )

        accepted.forEach { (name, payloads) ->
            quoteBtcDeposits { payloads }

            val request = env.depositService.confirmRequest(WALLET_ID, btcSource(), BigInteger.valueOf(AMOUNT), PerpsSourceScreen.PerpsHome)

            val deposit = request.type as ConfirmType.PerpetualDeposit
            assertEquals(name, "Relay", deposit.verifiedPayment?.provider)
        }
    }

    @Test
    fun aTaprootWalletDepositsItsOwnCoinsSignedWithTheDefaultSighash() = runTest {
        env.accounts[TestAssets.btc.id] = testAccount(TestAssets.btc, balance = BALANCE.toString(), address = TAPROOT_ADDRESS)
        quoteBtcDeposits {
            psbt(
                inputs = listOf(Input(TAPROOT_SCRIPT, IN, sighash = 0)),
                outputs = listOf(deposit(), Output(TAPROOT_SCRIPT, CHANGE), order()),
            )
        }

        val request = env.depositService.confirmRequest(WALLET_ID, btcSource(), BigInteger.valueOf(AMOUNT), PerpsSourceScreen.PerpsHome)

        val deposit = request.type as ConfirmType.PerpetualDeposit
        assertEquals(AMOUNT.toString(), deposit.verifiedPayment?.amount.toString())
        assertEquals(FEE.toString(), (deposit.operation.payloads.single().fee as Fee.Value).amount.toString())
    }

    @Test
    fun anyOtherTransactionFromBitcoinIsNoQuote() = runTest {
        val opened = refused.mapNotNull { (name, payloads) ->
            quoteBtcDeposits { payloads() }

            val failure = try {
                env.depositService.confirmRequest(WALLET_ID, btcSource(), BigInteger.valueOf(AMOUNT), PerpsSourceScreen.PerpsHome)
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
    fun theFeeMayGoUpToThreeTimesWhatTheWalletPaysAByteAndNoFurther() = runTest {
        val virtualSize = 218L
        val ceiling = WALLET_FEE_RATE * 3 * virtualSize

        quoteBtcDeposits { psbt(inputs = listOf(Input(SENDER_SCRIPT, AMOUNT + CHANGE + ceiling))) }
        val atTheCeiling = env.depositService.confirmRequest(WALLET_ID, btcSource(), BigInteger.valueOf(AMOUNT), PerpsSourceScreen.PerpsHome)
        val payload = (atTheCeiling.type as ConfirmType.PerpetualDeposit).operation.payloads.single()
        assertEquals(ceiling.toString(), (payload.fee as Fee.Value).amount.toString())

        quoteBtcDeposits { psbt(inputs = listOf(Input(SENDER_SCRIPT, AMOUNT + CHANGE + ceiling + 1))) }
        val failure = failureOf {
            env.depositService.confirmRequest(WALLET_ID, btcSource(), BigInteger.valueOf(AMOUNT), PerpsSourceScreen.PerpsHome)
        }
        assertTrue("$failure", failure is PerpsDepositException.QuoteRejected)
    }

    @Test
    fun aDepositFromBitcoinIsNoQuoteFromAnyProviderButRelay() = runTest {
        listOf("direct", "funxyz").forEach { provider ->
            env.quoteDeposits(provider = provider, credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) { psbt() }

            val failure = failureOf {
                env.depositService.confirmRequest(WALLET_ID, btcSource(), BigInteger.valueOf(AMOUNT), PerpsSourceScreen.PerpsHome)
            }

            assertTrue("$provider: $failure", failure is PerpsDepositException.QuoteRejected)
        }
    }

    @Test
    fun aDepositFromBitcoinIsNoQuoteWhenTheWalletCannotPriceAByte() = runTest {
        quoteBtcDeposits { psbt() }
        val unpriced = listOf(
            ChainRes.err<Fee>(ChainError.InternalError("node is down")),
            ChainRes.ok<Fee>(Fee.Value(Chain.Bitcoin.Mainnet.toAsset(), BigInteger.valueOf(780).toChain())),
        )

        unpriced.forEach { answer ->
            coEvery { env.provider.estimateFee(match<Payload> { it.meta is PayloadMeta.Bitcoin.Main }, any(), any()) } returns answer

            val failure = failureOf {
                env.depositService.confirmRequest(WALLET_ID, btcSource(), BigInteger.valueOf(AMOUNT), PerpsSourceScreen.PerpsHome)
            }

            assertTrue("$answer: $failure", failure is PerpsDepositException.QuoteRejected)
        }
    }

    @Test
    fun aDepositFromBitcoinIsNoQuoteWithoutBitcoinInTheSignedConfigAndOtherDepositsStillOpen() = runTest {
        val withoutBitcoin = listOf(
            env.securityBlock(
                relayDepositories = listOf(
                    "lighter" to JsonPrimitive(AGGREGATOR_ACCOUNT_INDEX.toString()),
                    "ton" to JsonPrimitive(PerpsSecurityBlocks.RELAY_TON_DEPOSITORY),
                ),
            ),
            env.securityBlock(relayDepositories = listOf("btc" to JsonPrimitive("relay"))),
            env.securityBlock(relayDepositories = listOf("btc" to JsonPrimitive("bc1qzmtn0q92ayejt2hpffvlktcpmyy7vvsd06sEFU"))),
            env.securityBlock(relayDepositories = listOf("eth" to JsonPrimitive(PerpsSecurityBlocks.RELAY_BTC_DEPOSITORY))),
            env.securityBlock(relayDepositories = null),
        )
        env.accounts[TestAssets.ton.id] = testAccount(TestAssets.ton, balance = "10000000000")
        val tonAmount = BigInteger.valueOf(3_000_000_000L)
        quoteBtcDeposits { asked ->
            if (asked.string("source_asset") == TestAssets.ton.id) {
                listOf(env.tonPayload("pay", value = tonAmount))
            } else {
                psbt()
            }
        }

        withoutBitcoin.forEach { block ->
            env.servedSecurityBlock = block

            val failure = failureOf {
                env.depositService.confirmRequest(WALLET_ID, btcSource(), BigInteger.valueOf(AMOUNT), PerpsSourceScreen.PerpsHome)
            }
            assertTrue("$block: $failure", failure is PerpsDepositException.QuoteRejected)
        }

        env.servedSecurityBlock = withoutBitcoin.first()
        val ton = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.ton.id), tonAmount, PerpsSourceScreen.PerpsHome)
        assertEquals("Relay", (ton.type as ConfirmType.PerpetualDeposit).verifiedPayment?.provider)
    }

    @Test
    fun aDepositFromBitcoinIsNoQuoteWithoutAVerifiedConfig() = runTest {
        env.servedSecurityBlock = null
        quoteBtcDeposits { psbt() }

        val failure = failureOf {
            env.depositService.confirmRequest(WALLET_ID, btcSource(), BigInteger.valueOf(AMOUNT), PerpsSourceScreen.PerpsHome)
        }

        assertTrue("$failure", failure is PerpsDepositException.QuoteRejected)
    }

    @Test
    fun aFirstDepositFromBitcoinChecksThePsbtItIsPricedOn() = runTest {
        env.withoutAccount()
        quoteBtcDeposits { asked ->
            if (asked.registersKey()) {
                listOf(env.authorizationPayload("authorization"), env.hermesPayload("execute"))
            } else {
                psbt(outputs = listOf(Output(FOREIGN_SCRIPT, AMOUNT), change(), order()))
            }
        }

        val failure = failureOf {
            env.depositService.confirmRequest(WALLET_ID, btcSource(), BigInteger.valueOf(AMOUNT), PerpsSourceScreen.PerpsHome)
        }

        assertTrue("$failure", failure is PerpsDepositException.QuoteRejected)
    }

    @Test
    fun aFirstDepositFromBitcoinQuotedAgainOnTheSwipeIsHeldToTheSameRules() = runTest {
        signPreviews()
        env.withoutAccount()
        var paid = psbt()
        quoteBtcDeposits { asked ->
            if (asked.registersKey() && "operation" !in asked) {
                listOf(env.authorizationPayload("authorization"), env.hermesPayload("execute"))
            } else {
                paid
            }
        }
        val previewed = env.depositService.confirmRequest(WALLET_ID, btcSource(), BigInteger.valueOf(AMOUNT), PerpsSourceScreen.PerpsHome)
        assertTrue(previewed.data.preview)
        val preview = previewed.type as ConfirmType.PerpetualDeposit
        assertEquals(FEE.toString(), (preview.paymentOperation?.payloads?.single()?.fee as Fee.Value).amount.toString())
        assertEquals("Relay", preview.verifiedPayment?.provider)
        assertEquals(AMOUNT.toString(), preview.verifiedPayment?.amount.toString())

        val authorized = env.depositService.authorize(previewed, mockk<MnemonicWallet>())
        assertFalse(authorized.data.preview)
        val deposit = authorized.type as ConfirmType.PerpetualDeposit
        assertEquals(FEE.toString(), (deposit.operation.payloads.single().fee as Fee.Value).amount.toString())
        assertEquals("Relay", deposit.verifiedPayment?.provider)

        paid = psbt(outputs = listOf(Output(FOREIGN_SCRIPT, AMOUNT), change(), order()))
        val foreign = failureOf { env.depositService.authorize(previewed, mockk<MnemonicWallet>()) }
        assertTrue("$foreign", foreign is PerpsDepositException.QuoteRejected)

        paid = psbt(inputs = listOf(Input(SENDER_SCRIPT, IN), Input(SENDER_SCRIPT, 1_000_000)))
        val inflated = failureOf { env.depositService.authorize(previewed, mockk<MnemonicWallet>()) }
        assertTrue("$inflated", inflated is PerpsDepositException.QuoteRejected)
        coVerify(exactly = 0) { env.provider.signPayload(any(), match { it.chain == Chain.Bitcoin.Mainnet }, any(), any(), any()) }
    }

    @Test
    fun aFirstDepositFromBitcoinWhosePsbtPaysMoreFeeOnTheSwipeIsShownAgain() = runTest {
        signPreviews()
        env.withoutAccount()
        var paid = psbt()
        quoteBtcDeposits { asked ->
            if (asked.registersKey() && "operation" !in asked) {
                listOf(env.authorizationPayload("authorization"), env.hermesPayload("execute"))
            } else {
                paid
            }
        }
        val previewed = env.depositService.confirmRequest(WALLET_ID, btcSource(), BigInteger.valueOf(AMOUNT), PerpsSourceScreen.PerpsHome)

        paid = psbt(inputs = listOf(Input(SENDER_SCRIPT, IN - 100)))
        val cheaper = env.depositService.authorize(previewed, mockk<MnemonicWallet>())
        assertEquals((FEE - 100).toString(), ((cheaper.type as ConfirmType.PerpetualDeposit).operation.payloads.single().fee as Fee.Value).amount.toString())

        paid = psbt(inputs = listOf(Input(SENDER_SCRIPT, IN + FEE / 100)))
        val withinOnePercent = env.depositService.authorize(previewed, mockk<MnemonicWallet>())
        assertFalse(withinOnePercent.data.preview)

        paid = psbt(inputs = listOf(Input(SENDER_SCRIPT, IN + 100)))
        val changed = failureOf { env.depositService.authorize(previewed, mockk<MnemonicWallet>()) }

        assertTrue("$changed", changed is QuoteChangedException)
        val requoted = (changed as QuoteChangedException).request
        assertFalse(requoted.data.preview)
        val payload = (requoted.type as ConfirmType.PerpetualDeposit).operation.payloads.single()
        assertEquals((FEE + 100).toString(), (payload.fee as Fee.Value).amount.toString())
        coVerify(exactly = 0) { env.provider.signPayload(any(), match { it.chain == Chain.Bitcoin.Mainnet }, any(), any(), any()) }
    }

    private fun psbt(
        inputs: List<Input> = listOf(Input(SENDER_SCRIPT, IN)),
        outputs: List<Output> = listOf(deposit(), change(), order()),
        id: String = "pay",
    ): List<JsonObject> = listOf(env.bitcoinPayload(id, psbt = TestPsbt.build(inputs, outputs)))

    private fun deposit(value: Long = AMOUNT) = Output(DEPOSITORY_SCRIPT, value)

    private fun change(value: Long = CHANGE) = Output(SENDER_SCRIPT, value)

    private fun order() = Output(ORDER_SCRIPT, 0)

    private fun quoteBtcDeposits(quotedFee: Long? = null, payloads: (asked: JsonObject) -> List<JsonObject>) {
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) { asked ->
            payloads(asked).map { payload -> quotedFee?.let { payload.withFee(it) } ?: payload }
        }
    }

    private fun JsonObject.withFee(amount: Long): JsonObject = JsonObject(
        this + ("fee" to buildJsonObject {
            put("type", "value")
            put("asset_id", TestAssets.btc.id)
            putJsonObject("data") { put("amount", amount.toString()) }
        }),
    )

    private fun btcSource(): PerpsDepositSource = env.source(TestAssets.btc.id)

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
        const val AMOUNT = 5_000L
        const val CHANGE = TestPsbt.CHANGE
        const val IN = AMOUNT + FEE + CHANGE
        const val BALANCE = 13_504L
        const val WALLET_FEE_RATE = 3L
        const val TAPROOT_ADDRESS = "bc1p0fa857n60fa857n60fa857n60fa857n60fa857n60fa857n60faqyqygk4"
        val TAPROOT_SCRIPT = "5120" + "7a".repeat(32)
        val CREDITED: BigInteger = BigInteger.valueOf(3_643_408L)
    }
}
