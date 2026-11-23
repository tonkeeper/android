package com.tonapps.deposit.multicoin.screens.confirm.battery

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.op.Intent
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.chainkit.core.op.ValidationError
import com.tonapps.chainkit.core.op.ValidationRes
import com.tonapps.deposit.multicoin.screens.confirm.fee.GaslessRelayer
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerOutcomeUnknownException
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerQuoteExpiredException
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerRejectedException
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.api.AuthorizationProvider
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.battery.entity.BatteryPurchaseQuote
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import io.batteryapi.models.GaslessV2SendResponse
import io.infrastructure.ClientError
import io.infrastructure.ClientException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.UUID

/**
 * A battery purchase paid on another chain: what is quoted, what the operation is held to before it is
 * signed, and how each way the battery can answer the send is told apart.
 */
class BatteryPurchaserTest : BaseUnitTest() {

    private val batteryRepository = mockk<BatteryRepository>()
    private val provider = mockk<ChainKitProvider>()
    private val gaslessRelayer = mockk<GaslessRelayer>()
    private val authorizationProvider = mockk<AuthorizationProvider>()
    private val cryptoWallet = mockk<MnemonicWallet>()
    private var validationEnabled = true

    private val purchaser = BatteryPurchaser(
        batteryRepository = batteryRepository,
        provider = provider,
        gaslessRelayer = gaslessRelayer,
        authorizationProvider = authorizationProvider,
        isValidationEnabled = { validationEnabled },
    )

    private val account by lazy { testAccount(TestAssets.usdtEth, balance = "50000000") }
    private val chain = Chain.Ethereum.Mainnet
    private val operation by lazy {
        Operation.build(
            id = OPERATION_ID,
            payloads = listOf(
                Payload(
                    id = "p_user",
                    type = Payload.Type.Eip7821HermesDelegate,
                    chain = chain,
                    version = Payload.VERSION,
                    fee = null,
                    meta = PayloadMeta.Evm.HermesDelegate(typedData = "{}"),
                ),
            ),
        )
    }

    private val posted = mutableListOf<SignedOperation>()
    private var answer: () -> GaslessV2SendResponse = { response(GaslessV2SendResponse.Status.ok, "0xhash") }

    @Before
    fun setUp() {
        coEvery { authorizationProvider.getAuthBy(TestWallet.ID) } returns Authorization(deviceToken = "device", walletId = TestWallet.ID)
        every { gaslessRelayer.parseOperation(any()) } returns operation
        coEvery { provider.validateOperation(any(), any(), any()) } returns ValidationRes.ok(Unit)
        coEvery { provider.signPayload(any(), any(), any(), any(), any()) } answers {
            val payload = firstArg<Payload>()
            SignRes.ok(SignedPayload.signed(payload, "signed-${payload.id}"))
        }
        coEvery { gaslessRelayer.send(any(), capture(posted)) } answers { answer() }
    }

    @Test
    fun quotesWhatTheRequestAsksFor() = runTest {
        coEvery { batteryRepository.emulatePurchase(any(), any(), any(), any(), any(), any()) } returns quote()

        val review = purchaser.quote(request())

        coVerify {
            batteryRepository.emulatePurchase(
                walletId = TestWallet.ID,
                chainId = "eth/mainnet",
                assetId = TestAssets.usdtEth.id,
                senderAddress = SENDER,
                amount = java.math.BigInteger(AMOUNT),
                promo = "PROMO",
            )
        }
        assertEquals(operation, review.operation)
        assertEquals(EXPIRES_AT, review.expiresAt)
        assertEquals(3_100L, review.quote.charges)
    }

    @Test
    fun aQuoteOfAnotherAmountIsNotSigned() = runTest {
        coEvery { batteryRepository.emulatePurchase(any(), any(), any(), any(), any(), any()) } returns quote(amount = "1")

        val error = runCatching { purchaser.quote(request()) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }

    @Test
    fun aQuoteWithoutAnOperationIsNotSigned() = runTest {
        coEvery { batteryRepository.emulatePurchase(any(), any(), any(), any(), any(), any()) } returns quote().copy(operation = null)

        val error = runCatching { purchaser.quote(request()) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }

    @Test
    fun signsAndSendsTheOperation() = runTest {
        val hash = purchaser.send(TestWallet.ID, review(), account, cryptoWallet)

        assertEquals("0xhash", hash)
        assertEquals(OPERATION_ID, posted.single().id)
        coVerify(exactly = 1) { provider.signPayload(any(), account.data.chainAccount, any(), any(), cryptoWallet) }
    }

    @Test
    fun theOperationIsHeldToTheAmountAndTheFundsReceiver() = runTest {
        val agreed = slot<Intent>()
        coEvery { provider.validateOperation(operation, account.value, capture(agreed)) } returns ValidationRes.ok(Unit)

        purchaser.send(TestWallet.ID, review(), account, cryptoWallet)

        val spend = agreed.captured.listed.single()
        assertEquals(TestAssets.usdtEth.id, spend.asset.id)
        assertEquals(BigInteger.parseString(AMOUNT), spend.amount)
        assertEquals(FUNDS_RECEIVER, agreed.captured.to?.display)
        assertEquals(Intent.Source.Wallet, agreed.captured.source)
    }

    @Test
    fun aDangerousOperationIsRefusedUnsigned() = runTest {
        val overspend = ValidationError.Overspend("0", TestAssets.usdtEth.id, BigInteger.ONE, BigInteger.TEN, BigInteger.ZERO)
        coEvery { provider.validateOperation(any(), any(), any()) } returns ValidationRes.verdict(listOf(overspend))

        val error = runCatching { purchaser.send(TestWallet.ID, review(), account, cryptoWallet) }.exceptionOrNull()

        assertTrue(error is RelayerRejectedException && error.nothingSent)
        coVerify(exactly = 0) { provider.signPayload(any(), any(), any(), any(), any()) }
        assertTrue(posted.isEmpty())
    }

    @Test
    fun aCheckThatReadNothingRefusesTheOperation() = runTest {
        coEvery { provider.validateOperation(any(), any(), any()) } returns
            ValidationRes.verdict(listOf(ValidationError.Unknown(IllegalStateException("parser broke"))))

        val error = runCatching { purchaser.send(TestWallet.ID, review(), account, cryptoWallet) }.exceptionOrNull()

        assertTrue(error is RelayerRejectedException)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun withValidationOffNothingIsChecked() = runTest {
        validationEnabled = false

        purchaser.send(TestWallet.ID, review(), account, cryptoWallet)

        coVerify(exactly = 0) { provider.validateOperation(any(), any(), any()) }
        assertEquals(1, posted.size)
    }

    @Test
    fun anExpiredOperationReadsAsExpired() = runTest {
        answer = { throw rejection(410, "emulation_expired") }

        val error = runCatching { purchaser.send(TestWallet.ID, review(), account, cryptoWallet) }.exceptionOrNull()

        assertTrue(error is RelayerQuoteExpiredException)
    }

    @Test
    fun aRejectionBeforeAnythingWasSentMayBeRetried() = runTest {
        answer = { throw rejection(400, "insufficient_funds") }

        val error = runCatching { purchaser.send(TestWallet.ID, review(), account, cryptoWallet) }.exceptionOrNull()

        assertTrue(error is RelayerRejectedException && error.nothingSent)
        assertEquals(BatteryPurchaseError.Rejected("rejected"), error!!.toPurchaseError())
    }

    @Test
    fun anUnansweredSendMayHavePaid() = runTest {
        answer = { throw IOException("socket closed") }

        val error = runCatching { purchaser.send(TestWallet.ID, review(), account, cryptoWallet) }.exceptionOrNull()

        assertTrue(error is RelayerOutcomeUnknownException)
        assertEquals(BatteryPurchaseError.OutcomeUnknown, error!!.toPurchaseError())
    }

    @Test
    fun aSendAlreadyMadeMayHavePaid() = runTest {
        answer = { throw rejection(409, "already_sent") }

        val error = runCatching { purchaser.send(TestWallet.ID, review(), account, cryptoWallet) }.exceptionOrNull()

        assertEquals(BatteryPurchaseError.OutcomeUnknown, error!!.toPurchaseError())
    }

    @Test
    fun aFailedStatusIsARejection() = runTest {
        answer = { response(GaslessV2SendResponse.Status.failed) }

        val error = runCatching { purchaser.send(TestWallet.ID, review(), account, cryptoWallet) }.exceptionOrNull()

        assertTrue(error is RelayerRejectedException)
    }

    @Test
    fun withoutBatteryAuthorizationNothingIsSigned() = runTest {
        coEvery { authorizationProvider.getAuthBy(TestWallet.ID) } returns Authorization.Empty

        val error = runCatching { purchaser.send(TestWallet.ID, review(), account, cryptoWallet) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        coVerify(exactly = 0) { provider.signPayload(any(), any(), any(), any(), any()) }
    }

    private fun request() = ConfirmRequest(
        data = CommonTransactionData(assetId = TestAssets.usdtEth.id, walletId = TestWallet.ID),
        type = ConfirmType.BatteryPurchase(
            chainId = "eth/mainnet",
            batteryAssetId = TestAssets.usdtEth.id,
            senderAddress = SENDER,
            amount = BigInteger.parseString(AMOUNT),
            symbol = "USDT",
            decimals = 6,
            estimatedCharges = 3_113,
            promo = "PROMO",
        ),
    )

    private fun quote(amount: String = AMOUNT) = BatteryPurchaseQuote(
        chainId = "eth/mainnet",
        assetId = TestAssets.usdtEth.id,
        senderAddress = SENDER,
        amount = java.math.BigInteger(amount),
        grossCharges = 3_113,
        charges = 3_100,
        fundsReceiver = FUNDS_RECEIVER,
        promoBonusPercent = null,
        operation = JsonObject(emptyMap()),
        expiresAt = EXPIRES_AT,
    )

    private fun review() = BatteryPurchaseReview(quote(), operation, EXPIRES_AT)

    private fun response(status: GaslessV2SendResponse.Status, txHash: String? = null) = GaslessV2SendResponse(
        operationId = UUID.fromString(OPERATION_ID),
        status = status,
        txHash = txHash,
    )

    private fun rejection(status: Int, code: String) = ClientException(
        message = "Client error : $status",
        statusCode = status,
        response = ClientError<Any>(
            body = """{"error": {"code": "$code", "message": "rejected"}}""",
            statusCode = status,
        ),
    )

    @Test
    fun idsCompareCaseInsensitivelyOnlyForEvmHex() {
        assertTrue(sameId("eth/mainnet/erc20/0xDAC17F958D2ee523a2206206994597C13D831ec7", "eth/mainnet/erc20/0xdac17f958d2ee523a2206206994597c13d831ec7"))
        assertTrue(sameId("0xAbC", "0xabc"))
        assertFalse(sameId("sol/mainnet/spl/EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", "sol/mainnet/spl/epjfwdd5aufqssqem2qn1xzybapc8g4weggkzwytdt1v"))
        assertFalse(sameId("TSender", "tsender"))
        assertTrue(sameId("TSender", "TSender"))
    }

    private companion object {
        const val AMOUNT = "10000000"
        const val SENDER = "0x1111111111111111111111111111111111111111"
        const val FUNDS_RECEIVER = "0x2222222222222222222222222222222222222222"
        const val OPERATION_ID = "bcef74e1-b940-4142-b7a7-76161850971e"
        const val EXPIRES_AT = 1_800_000_000_000L
    }
}
