package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Intent
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.chainkit.core.op.ValidationError
import com.tonapps.chainkit.core.op.ValidationRes
import com.tonapps.core.flags.BootFlags
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.SigningWallet
import com.tonapps.wallet.api.AuthorizationProvider
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.FakeTimeProvider
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.testing.testAsset
import io.batteryapi.models.GaslessV2SendResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A Solana transaction the relayer built, held to the user's intent before the wallet signs it: what
 * chainkit finds dangerous in it, or a check that read nothing, refuses it unsigned.
 */
class GaslessSenderSolanaTest : BaseUnitTest() {

    private val provider = mockk<ChainKitProvider>()
    private val gaslessRelayer = mockk<GaslessRelayer>()
    private val batteryRepository = mockk<BatteryRepository>(relaxed = true)
    private val authorizationProvider = mockk<AuthorizationProvider>()
    private val wallet = mockk<WalletEntity>()
    private val cryptoWallet = mockk<MnemonicWallet>()

    private val sender = GaslessSender(
        api = mockk(),
        authorizationProvider = authorizationProvider,
        batteryRepository = batteryRepository,
        pendingTransactionReporter = mockk(),
        provider = provider,
        gaslessRelayer = gaslessRelayer,
        timeProvider = FakeTimeProvider(),
        ledgerSigner = mockk(),
    )

    private val chain = Chain.Solana.Mainnet
    private val sol by lazy { testAccount(SOL, balance = "1000000000", address = SENDER) }
    private val battery = TxFee(
        account = FeeAccount.Keeper(balance = BigInteger.fromInt(100), rate = null),
        fee = Fee.Value(Asset.Battery(chain), BigInteger.ONE),
    )

    private val posted = mutableListOf<SignedOperation>()
    private val validated = mutableListOf<Pair<Operation, Intent>>()

    // What chainkit finds in the relayer's transaction.
    private var findings: List<ValidationError> = emptyList()

    @Before
    fun setUp() {
        BootFlags.update(BootFlags.values.copy(txValidation = true))
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
        coEvery { provider.validateOperation(any(), any(), any()) } answers {
            validated += firstArg<Operation>() to thirdArg<Intent>()
            ValidationRes.verdict(findings)
        }
        every { gaslessRelayer.send(any(), any()) } answers {
            posted += secondArg<SignedOperation>()
            GaslessV2SendResponse(
                operationId = UUID.fromString(OPERATION_ID),
                status = GaslessV2SendResponse.Status.ok,
                txHash = null,
            )
        }
    }

    @Test
    fun aTransactionWithNothingToFindIsSigned() = runTest {
        send(transfer())

        assertEquals(listOf(RELAYED_ID), posted.single().payloads.map { it.payloadId })
    }

    @Test
    fun theRelayersTransactionIsHeldToTheTransferTheUserAgreedTo() = runTest {
        send(transfer())

        val (operation, intent) = validated.single()
        assertEquals(OPERATION_ID, operation.id)
        assertEquals(listOf(sol.asset.value.id to BigInteger.fromInt(500_000)), intent.listed.map { it.asset.id to it.amount })
        assertEquals(RECIPIENT, intent.to?.display)
    }

    @Test
    fun aProviderSwapIsHeldToTheProgramItRuns() = runTest {
        send(providerSwap())

        assertEquals(PROGRAM, validated.single().second.to?.display)
    }

    @Test
    fun aDangerInTheRelayersTransactionRefusesItUnsigned() = runTest {
        findings = listOf(ValidationError.ForeignRecipient(RELAYED_ID, RECIPIENT, ELSEWHERE))

        val failure = failureOf { send(transfer()) }

        assertTrue(failure is RelayerRejectedException && !failure.mayHaveRelayed)
        coVerify(exactly = 0) { provider.signPayload(any(), any(), any(), any(), any()) }
        assertTrue(posted.isEmpty())
    }

    /** The finding is the refusal's reason, for the logs and analytics; the screen does not show it. */
    @Test
    fun anOverspendInTheRelayersTransactionRefusesItUnsignedWithTheFindingAsTheReason() = runTest {
        val overspend = ValidationError.Overspend(RELAYED_ID, SOL.id, BigInteger.ZERO, BigInteger.fromInt(80_000), BigInteger.ZERO)
        findings = listOf(overspend)

        val failure = failureOf { send(transfer()) }

        assertTrue(failure is RelayerRejectedException && failure.nothingSent)
        assertEquals(overspend.message, (failure as RelayerRejectedException).reason)
        coVerify(exactly = 0) { provider.signPayload(any(), any(), any(), any(), any()) }
        assertTrue(posted.isEmpty())
    }

    @Test
    fun aCheckThatReadNothingRefusesIt() = runTest {
        findings = listOf(ValidationError.Unknown(IllegalStateException("validator failed")))

        assertTrue(failureOf { send(transfer()) } is RelayerRejectedException)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun validationSwitchedOffChecksNothingBeforeSigning() = runTest {
        BootFlags.update(BootFlags.values.copy(txValidation = false))
        findings = listOf(ValidationError.Unknown(IllegalStateException("validator failed")))

        send(transfer())

        assertTrue(validated.isEmpty())
        assertEquals(1, posted.size)
    }

    @Test
    fun aFindingThatAsksNothingOfTheUserIsSigned() = runTest {
        // The operation confirm shows it before the swipe; it stops nothing there either.
        findings = listOf(ValidationError.BlindCall(RELAYED_ID, PROGRAM))

        send(providerSwap())

        assertEquals(1, posted.size)
    }

    private suspend fun send(call: GaslessRelayer.Call) {
        sender.sendOperation(wallet, sol.data.chainAccount, RelayerSigner.Software(SigningWallet.Hd(cryptoWallet)), battery, emulation(), listOf(call))
    }

    private fun transfer() = GaslessRelayer.solanaTransfer(sol, RECIPIENT, BigInteger.fromInt(500_000), memo = null)!!

    private fun providerSwap() = GaslessRelayer.swap(
        sol,
        Transaction.Swap(
            account = sol.value,
            amount = BigInteger.fromInt(500_000),
            destination = sol.value,
            to = Address.force(PROGRAM, chain),
            isMax = false,
            data = PROVIDER_TX,
            memo = null,
        ),
    )!!

    private fun emulation() = GaslessRelayer.Emulation(
        assetId = Asset.Battery.ID,
        fee = BigInteger.ONE,
        operation = Operation.build(
            id = OPERATION_ID,
            payloads = listOf(
                Payload(RELAYED_ID, Payload.Type.Prebuild, chain, Payload.VERSION, battery.fee, PayloadMeta.Solana.Prebuild(RELAYED_TX)),
            ),
        ),
        expiresAt = Long.MAX_VALUE,
    )

    private suspend fun failureOf(block: suspend () -> Unit): Throwable? {
        return try {
            block()
            null
        } catch (e: Throwable) {
            e
        }
    }

    private companion object {
        const val OPERATION_ID = "8e058bd8-8978-451d-971d-f9c0fb0f4b0e"
        const val RELAYED_ID = "38d370c0-76a1-4fa3-a11b-b0952ae407e5"
        const val SENDER = "hfwtABKJqoX8bd6ZVVz1jyCWBhuvJwEgAR1B2Ja16q2"
        const val RECIPIENT = "5Q544fKrFoe6tsEbD7S8EmxGTJYAKtTVhAW5Q5pge4j1"
        const val ELSEWHERE = "23iwJpnxc7GQXgHV3dySRaAYW5ugsDaCH1soFGaBAJ15"
        const val PROGRAM = "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4"
        const val PROVIDER_TX = "provider-transaction"
        const val RELAYED_TX = "relayed-transaction"

        val SOL = testAsset("sol/mainnet/coin", "SOL", "Solana", decimals = 9)
    }
}
