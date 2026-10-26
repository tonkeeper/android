package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.chainkit.core.chain.api.models.SignError
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.SigningWallet
import com.tonapps.wallet.api.AuthorizationProvider
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.data.battery.BatteryRepository
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
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.UUID

/**
 * An emulated EVM operation on its way back to the relayer: what is signed, what is posted, and how
 * each way the relayer can answer is told apart.
 */
class GaslessSenderEvmTest : BaseUnitTest() {

    private val provider = mockk<ChainKitProvider>()
    private val evmGasless = mockk<EvmGasless>()
    private val batteryRepository = mockk<BatteryRepository>(relaxed = true)
    private val authorizationProvider = mockk<AuthorizationProvider>()
    private val wallet = mockk<WalletEntity>()
    private val cryptoWallet = mockk<CryptoWallet>()

    private val sender = GaslessSender(
        api = mockk(),
        authorizationProvider = authorizationProvider,
        batteryRepository = batteryRepository,
        pendingTransactionReporter = mockk(),
        provider = provider,
        evmGasless = evmGasless,
    )

    private val chain = Chain.Ethereum.Mainnet
    private val account by lazy { testAccount(TestAssets.usdtEth).data.chainAccount }
    private val battery = TxFee(
        account = FeeAccount.Keeper(balance = BigInteger.fromInt(100), rate = null),
        fee = Fee.Value(Asset.Battery(chain), BigInteger.fromInt(19)),
    )
    private val operation by lazy {
        Operation.build(
            id = OPERATION_ID,
            payloads = listOf(
                Payload(
                    id = "p_auth",
                    type = Payload.Type.Eip7702Authorization,
                    chain = chain,
                    version = Payload.VERSION,
                    fee = null,
                    meta = PayloadMeta.Evm.Authorization(
                        chainId = 1,
                        address = Address.force("0x24b576BC271823bF9C24BE627B4363b9b00191e9", chain),
                        nonce = BigInteger.fromInt(41),
                    ),
                ),
                Payload(
                    id = "p_user",
                    type = Payload.Type.Eip7821HermesDelegate,
                    chain = chain,
                    version = Payload.VERSION,
                    fee = battery.fee,
                    meta = PayloadMeta.Evm.HermesDelegate(typedData = "{}"),
                ),
            ),
        )
    }

    private val posted = mutableListOf<SignedOperation>()
    private var answer: () -> GaslessV2SendResponse = { response(GaslessV2SendResponse.Status.ok) }

    @Before
    fun setUp() {
        every { wallet.id } returns TestWallet.ID
        coEvery { authorizationProvider.getAuthBy(TestWallet.ID) } returns Authorization(
            deviceToken = "device",
            walletId = TestWallet.ID,
        )
        coEvery { batteryRepository.getCharges(any(), any()) } returns 100
        coEvery { provider.signPayload(any(), any(), any(), any(), any()) } answers {
            val payload = firstArg<Payload>()
            SignRes.ok(SignedPayload.signed(payload, "signed-${payload.id}"))
        }
        every { evmGasless.send(any(), any()) } answers {
            posted += secondArg<SignedOperation>()
            answer()
        }
    }

    @Test
    fun everyDocumentIsSignedAndPostedUnderTheOperationsId() = runTest {
        send(emulation())

        val signed = posted.single()
        assertEquals(OPERATION_ID, signed.id)
        assertEquals(listOf("p_auth", "p_user"), signed.payloads.map { it.payloadId })
        assertEquals(
            listOf(JsonPrimitive("signed-p_auth"), JsonPrimitive("signed-p_user")),
            signed.payloads.map { it.data },
        )
        coVerify(exactly = 2) { provider.signPayload(any(), account, any(), any(), cryptoWallet) }
        verify(exactly = 1) { batteryRepository.refreshBalanceDelay(wallet) }
    }

    @Test
    fun anEmulationPastItsDeadlineIsNeitherSignedNorPosted() = runTest {
        val failure = failureOf { send(emulation(expiresAt = System.currentTimeMillis() - 1)) }

        assertTrue(failure is RelayerQuoteExpiredException)
        assertTrue(posted.isEmpty())
        coVerify(exactly = 0) { provider.signPayload(any(), any(), any(), any(), any()) }
    }

    @Test
    fun aDocumentThatCannotBeSignedPostsNothing() = runTest {
        coEvery { provider.signPayload(match { it.id == "p_user" }, any(), any(), any(), any()) } returns
            SignRes.err(SignError.InternalError("The Hermes document runs on another account"))

        val failure = failureOf { send(emulation()) }

        assertTrue(failure is IllegalStateException)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun anOperationTheRelayerNoLongerHoldsAsksForANewQuote() = runTest {
        answer = { throw rejection(404, "operation_not_found") }

        assertTrue(failureOf { send(emulation()) } is RelayerQuoteExpiredException)
    }

    @Test
    fun anyOtherRejectionCarriesTheRelayerReason() = runTest {
        answer = { throw rejection(400, "invalid_signature") }

        val failure = failureOf { send(emulation()) }
        assertTrue(failure is RelayerRejectedException)
        assertEquals("rejected", (failure as RelayerRejectedException).reason)
        assertTrue(failure.nothingSent)
    }

    @Test
    fun anOperationSentBeforeHasAnUnknownOutcome() = runTest {
        answer = { throw rejection(409, "already_sent") }

        assertTrue(failureOf { send(emulation()) } is RelayerOutcomeUnknownException)
    }

    @Test
    fun aFailedBroadcastMayHaveBeenSent() = runTest {
        answer = { throw rejection(400, "broadcast_failed") }

        val failure = failureOf { send(emulation()) }
        assertTrue(failure is RelayerRejectedException)
        assertFalse((failure as RelayerRejectedException).nothingSent)
    }

    @Test
    fun aRequestThatDiedOnTheWayHasAnUnknownOutcome() = runTest {
        answer = { throw IOException("timeout") }

        assertTrue(failureOf { send(emulation()) } is RelayerOutcomeUnknownException)
    }

    @Test
    fun aFailedStatusIsAFailure() = runTest {
        answer = { response(GaslessV2SendResponse.Status.failed) }

        assertTrue(failureOf { send(emulation()) } is RelayerRejectedException)
        verify(exactly = 0) { batteryRepository.refreshBalanceDelay(any()) }
    }

    @Test
    fun anUnknownStatusIsNotASuccess() = runTest {
        answer = { response(GaslessV2SendResponse.Status.unknown) }

        assertTrue(failureOf { send(emulation()) } is RelayerOutcomeUnknownException)
        verify(exactly = 0) { batteryRepository.refreshBalanceDelay(any()) }
    }

    private suspend fun send(emulation: EvmGasless.Emulation) {
        sender.sendEvm(wallet, account, SigningWallet.Hd(cryptoWallet), battery, emulation)
    }

    private suspend fun failureOf(block: suspend () -> Unit): Throwable? {
        return try {
            block()
            null
        } catch (e: Throwable) {
            e
        }
    }

    private fun emulation(expiresAt: Long = Long.MAX_VALUE) = EvmGasless.Emulation(
        assetId = Asset.Battery.ID,
        fee = battery.fee.amount,
        operation = operation,
        expiresAt = expiresAt,
    )

    private fun response(status: GaslessV2SendResponse.Status) = GaslessV2SendResponse(
        operationId = UUID.fromString(OPERATION_ID),
        status = status,
    )

    private fun rejection(status: Int, code: String) = ClientException(
        message = "Client error : $status",
        statusCode = status,
        response = ClientError<Any>(
            body = """{"error": {"code": "$code", "message": "rejected"}}""",
            statusCode = status,
        ),
    )

    private companion object {
        const val OPERATION_ID = "bcef74e1-b940-4142-b7a7-76161850971e"
    }
}
