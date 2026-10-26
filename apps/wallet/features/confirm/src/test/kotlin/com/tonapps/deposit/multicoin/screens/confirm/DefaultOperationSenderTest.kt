package com.tonapps.deposit.multicoin.screens.confirm

import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.DefaultOperationSender
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

/**
 * A signed operation on its way to the chain: what chainkit is handed for each payload, in what
 * order, and what a retry sends after a broadcast that failed half way.
 */
class DefaultOperationSenderTest : BaseUnitTest() {

    private val provider = mockk<ChainKitProvider>()
    private val sender = DefaultOperationSender(provider)
    private val broadcast = mutableListOf<Pair<Account, String>>()
    private val refused = mutableMapOf<String, NodeError>()

    private val ethereum: Account by lazy { testAccount(TestAssets.eth).data.chainAccount }
    private val ton: Account by lazy { testAccount(TestAssets.ton).data.chainAccount }
    private val bitcoin: Account by lazy { testAccount(TestAssets.btc).data.chainAccount }

    @Before
    fun setUp() {
        coEvery { provider.broadcastPayload(any(), any()) } answers {
            val transaction = (firstArg<SignedPayload>().data as JsonPrimitive).content
            val error = refused[transaction]
            if (error != null) {
                NodeRes.err(error)
            } else {
                broadcast += secondArg<Account>() to transaction
                NodeRes.ok("hash-of-$transaction")
            }
        }
    }

    @Test
    fun everyPayloadIsBroadcastInOrderFromTheAccountThatSignedIt() = runTest {
        sender.send(TestWallet.ID, ethereum, operation(evm("approve", "0xaa"), evm("deposit", "0xbb")))

        assertEquals(listOf("0xaa", "0xbb"), broadcast.map { it.second })
        broadcast.forEach { (from, _) -> assertSame(ethereum, from) }
    }

    @Test
    fun aTonMessageGoesToChainkitAsSigned() = runTest {
        val boc = Base64.getEncoder().encodeToString(byteArrayOf(0xb5.toByte(), 0xee.toByte(), 0x9c.toByte(), 0x72))
        val message = SignedPayload("message", SignedPayload.Type.Main, ton.chain, JsonPrimitive(boc))

        sender.send(TestWallet.ID, ton, operation(message))

        assertEquals(listOf(ton to boc), broadcast)
    }

    @Test
    fun aPrebuiltBitcoinTransactionGoesToChainkitAsSigned() = runTest {
        val prebuilt = SignedPayload("pay", SignedPayload.Type.Prebuild, bitcoin.chain, JsonPrimitive("02000000000101"))

        sender.send(TestWallet.ID, bitcoin, operation(prebuilt))

        assertEquals(listOf(bitcoin to "02000000000101"), broadcast)
    }

    @Test
    fun aBroadcastTheNodeRefusesStopsTheRestAndSurfacesTheNodesError() = runTest {
        val rejection = NodeError.Rejected(code = -32000, message = "nonce too low")
        refused["0xbb"] = rejection

        val failure = failureOf {
            sender.send(TestWallet.ID, ethereum, operation(evm("first", "0xaa"), evm("second", "0xbb"), evm("third", "0xcc")))
        }

        assertSame(rejection, failure)
        // The third depends on the second: it is not sent ahead of it.
        assertEquals(listOf("0xaa"), broadcast.map { it.second })
    }

    @Test
    fun aRetrySendsOnlyWhatTheChainHasNotTakenYet() = runTest {
        val operation = operation(evm("approve", "0xaa"), evm("deposit", "0xbb"))
        refused["0xbb"] = NodeError.NetworkWrap(IllegalStateException("timeout"))
        failureOf { sender.send(TestWallet.ID, ethereum, operation) }
        assertEquals(listOf("0xaa"), broadcast.map { it.second })

        refused.clear()
        sender.send(TestWallet.ID, ethereum, operation)

        // The approve went out once: sending it again would only be refused for its nonce.
        assertEquals(listOf("0xaa", "0xbb"), broadcast.map { it.second })
    }

    @Test
    fun anotherOperationIsSentInFullWhateverItsPayloadsAreCalled() = runTest {
        sender.send(TestWallet.ID, ethereum, SignedOperation("first", listOf(evm("0", "0xaa"))))

        sender.send(TestWallet.ID, ethereum, SignedOperation("second", listOf(evm("0", "0xbb"))))

        assertEquals(listOf("0xaa", "0xbb"), broadcast.map { it.second })
    }

    @Test
    fun anOperationWithADocumentOnlyARelayerSubmitsSendsNothing() = runTest {
        val authorization = SignedPayload(
            payloadId = "authorization",
            type = SignedPayload.Type.Eip7702Authorization,
            chain = Chain.Ethereum.Mainnet,
            data = buildJsonObject { put("r", "0x01") },
        )

        val failure = failureOf { sender.send(TestWallet.ID, ethereum, operation(evm("approve", "0xaa"), authorization)) }

        assertTrue(failure is IllegalArgumentException)
        // Not even the approve ahead of it: the operation is sent whole or not at all.
        assertTrue(broadcast.isEmpty())
    }

    @Test
    fun aHermesSignatureIsNotATransactionThoughItIsAString() = runTest {
        val delegated = SignedPayload("batch", SignedPayload.Type.Eip7821HermesDelegate, Chain.Ethereum.Mainnet, JsonPrimitive("0xsignature"))

        val failure = failureOf { sender.send(TestWallet.ID, ethereum, operation(delegated)) }

        assertTrue(failure is IllegalArgumentException)
        assertTrue(broadcast.isEmpty())
    }

    private fun operation(vararg payloads: SignedPayload) = SignedOperation("operation-1", payloads.toList())

    private fun evm(id: String, transaction: String): SignedPayload {
        return SignedPayload(id, SignedPayload.Type.Main, Chain.Ethereum.Mainnet, JsonPrimitive(transaction))
    }

    private suspend fun failureOf(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (e: Throwable) {
            return e
        }
        throw AssertionError("The send was expected to fail")
    }
}
