package com.tonapps.deposit.multicoin.screens.confirm.fee

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.cellFromBase64
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.chain.model.account.PrivateKey
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.chainkit.core.op.ValidationRes
import com.tonapps.core.flags.BootFlags
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.SigningWallet
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.AuthorizationProvider
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.FakeTimeProvider
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import io.batteryapi.models.GaslessV2SendResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.cell.buildCell
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletTransfer

/**
 * A TON message the relayer delivers is signed by the wallet's own code, as the battery's messages
 * are: a request another contract hands the wallet, at the wallet's seqno, good until the relayer's
 * deadline. Chainkit is not asked to sign it.
 */
class GaslessSenderTonTest : BaseUnitTest() {

    private val provider = mockk<ChainKitProvider>()
    private val gaslessRelayer = mockk<GaslessRelayer>()
    private val batteryRepository = mockk<BatteryRepository>(relaxed = true)
    private val authorizationProvider = mockk<AuthorizationProvider>()
    private val api = mockk<API>()
    private val wallet = mockk<WalletEntity>()
    private val cryptoWallet = mockk<MnemonicWallet>()

    private val sender = GaslessSender(
        api = api,
        authorizationProvider = authorizationProvider,
        batteryRepository = batteryRepository,
        pendingTransactionReporter = mockk(),
        provider = provider,
        gaslessRelayer = gaslessRelayer,
        timeProvider = FakeTimeProvider(),
        ledgerSigner = mockk(),
    )

    private val chain = Chain.Ton.Mainnet
    private val jetton by lazy { testAccount(TestAssets.usdtTon, balance = "50000000", address = TestWallet.TON_ADDRESS) }
    private val battery = TxFee(
        account = FeeAccount.Keeper(balance = BigInteger.fromInt(1000), rate = null),
        fee = Fee.Value(Asset.Battery(chain), BigInteger.fromInt(102)),
    )

    private val unsigned = buildCell { storeUInt(1, 8) }
    private val signed = buildCell { storeUInt(2, 8) }

    private val gifts = slot<List<WalletTransfer>>()
    private val posted = mutableListOf<SignedOperation>()

    @Before
    fun setUp() {
        BootFlags.update(BootFlags.values.copy(txValidation = true))
        every { wallet.id } returns TestWallet.ID
        every { wallet.accountId } returns ACCOUNT_ID
        every { wallet.network } returns TonNetwork.MAINNET
        every { wallet.createBody(any(), any(), capture(gifts), any()) } returns unsigned
        every { wallet.sign(any(), any(), any()) } returns signed
        every { api.getAccountSeqno(ACCOUNT_ID, TonNetwork.MAINNET) } returns SEQNO
        every { api.getServerTime(TonNetwork.MAINNET) } returns NOW.toInt()
        every { cryptoWallet.getPrivateKey(chain) } returns mockk<PrivateKey> {
            every { data() } returns ByteArray(KEY_SIZE) { 1 }
        }
        coEvery { authorizationProvider.getAuthBy(TestWallet.ID) } returns Authorization(
            deviceToken = "device",
            walletId = TestWallet.ID,
        )
        coEvery { batteryRepository.getCharges(any(), any()) } returns 1000
        coEvery { provider.validateOperation(any(), any(), any()) } returns ValidationRes.verdict(emptyList())
        every { gaslessRelayer.send(any(), any()) } answers {
            posted += secondArg<SignedOperation>()
            GaslessV2SendResponse(
                operationId = UUID.fromString(OPERATION_ID),
                status = GaslessV2SendResponse.Status.ok,
                txHash = TX_HASH,
            )
        }
    }

    @Test
    fun theMessageIsSignedAsARequestTheRelayerDelivers() = runTest {
        val txHash = send(answered())

        verify { wallet.createBody(seqNo = SEQNO, validUntil = EXPIRE_AT, gifts = any(), internalMessage = true) }
        verify { wallet.sign(any(), SEQNO, unsigned) }
        val payload = posted.single().payloads.single()
        assertEquals(RELAYED_ID, payload.payloadId)
        assertEquals(SignedPayload.Type.Main, payload.type)
        assertEquals(JsonPrimitive(signed.base64()), payload.data)
        assertEquals(TX_HASH, txHash)
        coVerify(exactly = 0) { provider.signPayload(any(), any(), any(), any(), any()) }
    }

    @Test
    fun theMessageSignedIsTheOneTheRelayerAnswered() = runTest {
        send(answered())

        val gift = gifts.captured.single()
        assertEquals(AddrStd.parse(ROUTER_RAW), gift.destination)
        assertEquals(Coins.ofNano(VALUE.longValue(exactRequired = true)), gift.coins.coins)
        assertTrue(gift.bounceable)
        assertEquals(SEND_MODE, gift.sendMode)
    }

    @Test
    fun theRelayersMessageIsHeldToWhatTheSwapSells() = runTest {
        send(answered())

        coVerify { provider.validateOperation(match { it.id == OPERATION_ID }, any(), match { it.listed.single().amount == SOLD }) }
    }

    /** The request is good until the relayer's deadline, but never long: whoever holds it can send it until then. */
    @Test
    fun aFarDeadlineIsCutShort() = runTest {
        send(answered().copy(expireAt = NOW + DAY))

        verify { wallet.createBody(seqNo = SEQNO, validUntil = NOW + MAX_TTL, gifts = any(), internalMessage = true) }
    }

    @Test
    fun aDeadlineAlreadyPastIsNotSigned() = runTest {
        val failure = failureOf { send(answered().copy(expireAt = NOW)) }

        assertTrue(failure is RelayerQuoteExpiredException)
        assertTrue(posted.isEmpty())
    }

    /** What is signed is the message asked for: the relayer's answer only sets how long it is good. */
    @Test
    fun theMessageAskedForIsWhatIsSigned() = runTest {
        send(answered().copy(body = OTHER_BODY, bounce = false))

        val gift = gifts.captured.single()
        assertTrue(gift.bounceable)
        assertEquals(BODY.cellFromBase64(), (gift.messageData as MessageData.Raw).body)
    }

    /** A message asked in a mode of its own, the whole balance, is signed in that mode. */
    @Test
    fun aMessageIsSignedInTheModeItWasAskedIn() = runTest {
        send(answered().copy(mode = WHOLE_BALANCE_MODE), asked().copy(mode = WHOLE_BALANCE_MODE))

        assertEquals(WHOLE_BALANCE_MODE, gifts.captured.single().sendMode)
    }

    @Test
    fun aMessageInAnotherSendModeIsNotSigned() = runTest {
        val failure = failureOf { send(answered().copy(mode = WHOLE_BALANCE_MODE)) }

        assertTrue(failure is IllegalStateException)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun aMessageThatDeploysIsSignedWithItsStateInit() = runTest {
        send(answered().copy(stateInit = BODY), asked().copy(stateInit = BODY))

        val gift = gifts.captured.single()
        assertEquals(BODY.cellFromBase64(), (gift.messageData as MessageData.Raw).stateInit?.cell)
    }

    /** A message that states a comment and no body is sent with the comment as its body. */
    @Test
    fun aCommentIsSentAsTheBody() = runTest {
        val commented = asked().copy(body = null, comment = "deposit-42")

        send(answered().copy(body = null, comment = "deposit-42"), commented)

        val gift = gifts.captured.single()
        assertEquals(GaslessRelayer.deliveredBody(commented)!!.cellFromBase64(), (gift.messageData as MessageData.Raw).body)
    }

    @Test
    fun aJettonDepositIsNotSignedHere() = runTest {
        val deposit = asked().copy(body = null, token = Address.force(ROUTER_RAW, chain))

        val failure = failureOf { send(answered().copy(body = null), deposit) }

        assertTrue(failure is IllegalStateException)
        assertTrue(posted.isEmpty())
    }

    /** The parts of a split route are signed at the seqnos the relayer states for them. */
    @Test
    fun eachPartIsSignedAtTheRelayersSeqno() = runTest {
        sendParts(seqnos = listOf(SEQNO.toLong(), SEQNO + 1L))

        verify { wallet.createBody(seqNo = SEQNO, validUntil = any(), gifts = any(), internalMessage = true) }
        verify { wallet.createBody(seqNo = SEQNO + 1, validUntil = any(), gifts = any(), internalMessage = true) }
        assertEquals(2, posted.single().payloads.size)
    }

    /** Where the relayer states no seqno, the parts go at the wallet's own and the ones after it. */
    @Test
    fun partsWithNoSeqnoFollowTheWallets() = runTest {
        sendParts(seqnos = null)

        verify { wallet.createBody(seqNo = SEQNO, validUntil = any(), gifts = any(), internalMessage = true) }
        verify { wallet.createBody(seqNo = SEQNO + 1, validUntil = any(), gifts = any(), internalMessage = true) }
    }

    /** A batch states its deadline on itself, not on its messages: the request is good until it. */
    @Test
    fun aBatchIsGoodUntilItsOwnDeadline() = runTest {
        val emulation = GaslessRelayer.Emulation(
            assetId = Asset.Battery.ID,
            fee = battery.fee.amount,
            operation = Operation.build(
                id = OPERATION_ID,
                payloads = listOf(Payload(RELAYED_ID, Payload.Type.Batch, chain, Payload.VERSION, battery.fee, PayloadMeta.Ton.Batch(calls = listOf(answered().copy(expireAt = null))))),
            ),
            expiresAt = Long.MAX_VALUE,
            tonDeadlines = mapOf(RELAYED_ID to NOW + 60),
        )

        sender.sendOperation(
            wallet = wallet,
            account = jetton.data.chainAccount,
            signer = RelayerSigner.Software(SigningWallet.Hd(cryptoWallet)),
            option = battery,
            emulation = emulation,
            calls = listOf(GaslessRelayer.tonSwap(jetton, asked(), SOLD)!!),
        )

        verify { wallet.createBody(seqNo = SEQNO, validUntil = NOW + 60, gifts = any(), internalMessage = true) }
    }

    /** A message after one that carries the whole balance would be skipped: such a batch is not signed. */
    @Test
    fun aMessageAfterTheWholeBalanceIsNotSigned() = runTest {
        val whole = answered().copy(mode = WHOLE_BALANCE_MODE)
        val emulation = GaslessRelayer.Emulation(
            assetId = Asset.Battery.ID,
            fee = battery.fee.amount,
            operation = Operation.build(
                id = OPERATION_ID,
                payloads = listOf(Payload(RELAYED_ID, Payload.Type.Batch, chain, Payload.VERSION, battery.fee, PayloadMeta.Ton.Batch(calls = listOf(whole, answered())))),
            ),
            expiresAt = Long.MAX_VALUE,
        )

        val failure = failureOf {
            sender.sendOperation(
                wallet = wallet,
                account = jetton.data.chainAccount,
                signer = RelayerSigner.Software(SigningWallet.Hd(cryptoWallet)),
                option = battery,
                emulation = emulation,
                calls = listOf(GaslessRelayer.tonSwap(jetton, asked().copy(mode = WHOLE_BALANCE_MODE), SOLD)!!, GaslessRelayer.tonSwap(jetton, asked(), SOLD)!!),
            )
        }

        assertTrue(failure?.message.orEmpty().contains("after one that carries all"))
        assertTrue(posted.isEmpty())
    }

    private suspend fun sendParts(seqnos: List<Long>?) {
        val ids = listOf(RELAYED_ID, SECOND_ID)
        val emulation = GaslessRelayer.Emulation(
            assetId = Asset.Battery.ID,
            fee = battery.fee.amount,
            operation = Operation.build(
                id = OPERATION_ID,
                payloads = ids.map { Payload(it, Payload.Type.Main, chain, Payload.VERSION, battery.fee, answered()) },
            ),
            expiresAt = Long.MAX_VALUE,
            tonSeqnos = seqnos?.let { ids.zip(it).toMap() }.orEmpty(),
        )
        val call = GaslessRelayer.tonSwap(jetton, asked(), SOLD)!!
        sender.sendOperation(
            wallet = wallet,
            account = jetton.data.chainAccount,
            signer = RelayerSigner.Software(SigningWallet.Hd(cryptoWallet)),
            option = battery,
            emulation = emulation,
            calls = listOf(call, call),
        )
    }

    private suspend fun send(meta: PayloadMeta.Ton.Main, asked: PayloadMeta.Ton.Main = asked()): String? {
        val call = GaslessRelayer.tonSwap(jetton, asked, SOLD)!!
        return sender.sendOperation(
            wallet = wallet,
            account = jetton.data.chainAccount,
            signer = RelayerSigner.Software(SigningWallet.Hd(cryptoWallet)),
            option = battery,
            emulation = emulation(meta),
            calls = listOf(call),
        )
    }

    private fun asked() = PayloadMeta.Ton.Main(
        to = Address.force(ROUTER_RAW, chain),
        value = VALUE,
        body = BODY,
    )

    private fun answered() = PayloadMeta.Ton.Main(
        to = Address.force(ROUTER_RAW, chain),
        value = VALUE,
        body = BODY,
        bounce = true,
        mode = SEND_MODE,
        expireAt = EXPIRE_AT,
    )

    private fun emulation(meta: PayloadMeta.Ton.Main) = GaslessRelayer.Emulation(
        assetId = Asset.Battery.ID,
        fee = battery.fee.amount,
        operation = Operation.build(
            id = OPERATION_ID,
            payloads = listOf(Payload(RELAYED_ID, Payload.Type.Main, chain, Payload.VERSION, battery.fee, meta)),
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
        const val OPERATION_ID = "44385269-d635-4049-aedb-a2737761264b"
        const val SECOND_ID = "6a693eba-1cd9-4703-87b6-847498c9c0a1"
        const val RELAYED_ID = "5a693eba-1cd9-4703-87b6-847498c9c0a0"
        const val ACCOUNT_ID = "0:b5cc75c314329e8109a446e3e34fd1ba999fbda43569f3dcd45ab5c0dd67f2a8"
        const val ROUTER_RAW = "0:88f8885fe9daa2bbaaa532976e49eb9fa99752189b122c0a5ac4fb9322cf6ac2"
        const val BODY = "te6cckEBAQEAAgAAAEysuc0="
        const val TX_HASH = "f0a1c2d3e4b5968778695a4b3c2d1e0ff0a1c2d3e4b5968778695a4b3c2d1e0f"
        const val SEQNO = 277
        const val EXPIRE_AT = 1794838489L
        const val NOW = EXPIRE_AT - 300
        const val DAY = 86_400L
        const val MAX_TTL = 600L
        const val OTHER_BODY = "te6ccgEBAQEABgAACA+KfqU="
        const val SEND_MODE = 3
        const val WHOLE_BALANCE_MODE = 128
        const val KEY_SIZE = 32

        val VALUE: BigInteger = BigInteger.fromInt(260_000_000)
        val SOLD: BigInteger = BigInteger.parseString("50000000000")
    }
}
