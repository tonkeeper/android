package com.tonapps.deposit.multicoin.screens.confirm

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A confirm request travels serialized, as JSON in fragment arguments and inside navigation routes,
 * so every type has to come back as itself — the ones grouped under [ConfirmType.Operational]
 * included.
 */
class ConfirmTypeSerializationTest : BaseUnitTest() {

    // Built on first use: an address is validated by the native lib the rule loads.
    private val operation by lazy {
        ConfirmOperations.transfer(
            chain = Chain.Ethereum.Mainnet,
            to = TestWallet.ETH_ADDRESS,
            amount = BigInteger.fromInt(1_000),
        )
    }

    @Test
    fun aTransferComesBackWithItsOperation() {
        val decoded = roundTrip(ConfirmType.Transfer(operation, domain = "alice.ton", isMax = true)) as ConfirmType.Transfer

        assertOperation(decoded)
        assertEquals("alice.ton", decoded.domain)
        assertEquals(true, decoded.isMax)
    }

    @Test
    fun aCallComesBackWithItsOperation() {
        assertOperation(roundTrip(ConfirmType.Call(operation)) as ConfirmType.Call)
    }

    @Test
    fun aPerpsDepositComesBackWithItsQuote() {
        val deposit = ConfirmType.PerpetualDeposit(
            operation = operation,
            amount = BigInteger.fromInt(5_000_000),
            minReceive = BigInteger.fromInt(4_900_000),
            quote = QUOTE,
            feeShareBps = 3_700,
        )

        val decoded = roundTrip(deposit) as ConfirmType.PerpetualDeposit

        assertOperation(decoded)
        assertEquals(deposit.amount, decoded.amount)
        assertEquals(deposit.minReceive, decoded.minReceive)
        assertEquals(deposit.quote, decoded.quote)
        assertEquals(3_700, decoded.feeShareBps)
    }

    @Test
    fun aDirectPerpsDepositHasNoMinimum() {
        val deposit = ConfirmType.PerpetualDeposit(operation, amount = BigInteger.TEN, quote = QUOTE)

        val decoded = roundTrip(deposit) as ConfirmType.PerpetualDeposit
        assertNull(decoded.minReceive)
        assertNull(decoded.feeShareBps)
    }

    @Test
    fun theTypesWithoutAnOperationComeBackToo() {
        val withdraw = ConfirmType.PerpetualWithdraw(amount = BigInteger.fromInt(10_000_000))
        val swap = ConfirmType.Swap(sourceAmount = BigInteger.TEN, destinationAssetId = TestAssets.usdtEth.id)

        assertEquals(withdraw, roundTrip(withdraw))
        assertEquals(swap, roundTrip(swap))
    }

    private fun roundTrip(type: ConfirmType): ConfirmType {
        val request = ConfirmRequest(
            data = CommonTransactionData(assetId = TestAssets.eth.id, walletId = TestWallet.ID),
            type = type,
        )
        return Json.decodeFromString<ConfirmRequest>(Json.encodeToString(request)).type
    }

    private fun assertOperation(decoded: ConfirmType) {
        assertTrue(decoded is ConfirmType.Operational)
        val restored = (decoded as ConfirmType.Operational).operation
        assertEquals(operation.id, restored.id)
        assertEquals(operation.payloads, restored.payloads)
    }
}

private val QUOTE = ConfirmType.PerpetualDeposit.Quote(
    id = "quote-1",
    executionId = "execution-1",
    beneficiary = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8",
    depositAmount = BigInteger.fromInt(4_900_000),
    crossChain = true,
)
