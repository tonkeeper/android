package com.tonapps.wallet.data.multichain.tx

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.wallet.api.API
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.walletapi.models.ActivityType
import io.walletapi.models.PendingTransaction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A payload sent without a swap is reported as the activity it reads as: a coin or token send, or
 * a contract call.
 */
class RemotePendingTransactionReporterTest {

    private val reported = slot<PendingTransaction>()
    private val api = mockk<API> {
        every { multichain.wallets.addPendingTransactions(any(), capture(reported), any()) } returns mockk(relaxed = true)
    }
    private val reporter = RemotePendingTransactionReporter(api)

    // Who it goes to is not what it reads as; a real address needs wallet-core, which this module does not load.
    private val address = mockk<Address>()

    @Test
    fun aCoinTransferIsASend() = runTest {
        report(PayloadMeta.Evm.Main(to = address, value = BigInteger.ONE))

        assertEquals(ActivityType.send, reported.captured.activityType)
    }

    @Test
    fun aTokenTransferIsASend() = runTest {
        report(PayloadMeta.Evm.Main(to = address, value = BigInteger.ZERO, data = TRANSFER_CALLDATA))

        assertEquals(ActivityType.send, reported.captured.activityType)
    }

    @Test
    fun aTokenTransferThatAlsoMovesTheCoinIsAContractCall() = runTest {
        report(PayloadMeta.Evm.Main(to = address, value = BigInteger.ONE, data = TRANSFER_CALLDATA))

        assertEquals(ActivityType.contract_call, reported.captured.activityType)
    }

    @Test
    fun anyOtherCallIsAContractCall() = runTest {
        report(PayloadMeta.Evm.Main(to = address, value = BigInteger.ZERO, data = "0x095ea7b3"))

        assertEquals(ActivityType.contract_call, reported.captured.activityType)
    }

    private suspend fun report(meta: PayloadMeta) {
        reporter.onPayloadSent(
            walletId = "wallet-1",
            account = mockk(),
            payload = Payload(id = "main", type = Payload.Type.Main, chain = Chain.Ethereum.Mainnet, version = 1, fee = null, meta = meta),
            txHash = "0xhash",
        )
    }

    private companion object {
        const val TRANSFER_CALLDATA = "0xa9059cbb" +
            "0000000000000000000000003b4d794a66304f130a4db8f2551b0070dfcf5ca7" +
            "00000000000000000000000000000000000000000000000000000000000f4240"
    }
}
