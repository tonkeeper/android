package com.tonapps.perps.funding

import com.tonapps.chainkit.core.chain.api.models.TxError
import com.tonapps.chainkit.core.chain.api.models.TxRes
import com.tonapps.chainkit.core.chain.model.transaction.TransactionInfo
import com.tonapps.chainkit.core.common.NetworkError
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.testAccount
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PerpsTransactionLookupTest : BaseUnitTest() {

    private val mcAccountRepository = mockk<McAccountRepository>()
    private val provider = mockk<ChainKitProvider>()
    private val lookup = PerpsTransactionLookup(mockk(), provider, mcAccountRepository)

    @Before
    fun setUp() {
        coEvery { mcAccountRepository.findAccount(WALLET_ID, TestAssets.btc.id, any(), any()) } returns testAccount(TestAssets.btc)
        coEvery { mcAccountRepository.findAccount(WALLET_ID, TestAssets.trx.id, any(), any()) } returns testAccount(TestAssets.trx)
    }

    @Test
    fun aBitcoinTxidBlockbookDoesNotKnowIsUnknown() = runTest {
        coEvery { provider.findTransaction(any(), TXID) } returns
            lookupFailure("GET https://bitcoin.atomicwallet.io: Transaction '$TXID' not found")

        assertEquals(PerpsTransactionPresence.UNKNOWN, presence(TestAssets.btc))
    }

    @Test
    fun aBlockbookBackendFailureLeavesTheBitcoinTxidUnavailable() = runTest {
        coEvery { provider.findTransaction(any(), TXID) } returns
            lookupFailure("GET https://bitcoin.atomicwallet.io: Transaction '$TXID' not found (connection refused)")

        assertEquals(PerpsTransactionPresence.UNAVAILABLE, presence(TestAssets.btc))
    }

    @Test
    fun aNotFoundTextOffBitcoinLeavesTheTxidUnavailable() = runTest {
        coEvery { provider.findTransaction(any(), TXID) } returns
            lookupFailure("POST https://multi.tonkeeper.com: Transaction '$TXID' not found")

        assertEquals(PerpsTransactionPresence.UNAVAILABLE, presence(TestAssets.trx))
    }

    private fun lookupFailure(message: String): TxRes<TransactionInfo> {
        return TxRes.err(TxError.Unknown(NetworkError(BAD_REQUEST, message)))
    }

    private suspend fun presence(asset: AssetEntity): PerpsTransactionPresence {
        return lookup.presence(WALLET_ID, asset.id, asset.value.chain, TXID)
    }

    private companion object {
        const val WALLET_ID = "wallet"
        const val TXID = "9ccc561b0f1b5f38e2a3c5d7e9f1a3b5c7d9e1f3a5b7c9d1e3f5a7b9c1d35f38"
        const val BAD_REQUEST = 400
    }
}
