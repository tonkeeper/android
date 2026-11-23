package com.tonapps.wallet.data.dapps.evm

import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EvmProviderRepositoryTest {

    private val repository = EvmProviderRepository()

    private val request = ConfirmRequest(
        data = CommonTransactionData(assetId = "eip155/1/coin", walletId = "wallet"),
        type = ConfirmType.Message("0x68690000", ConfirmType.Message.Type.Personal),
        context = ConfirmContext.Dapp(
            requestId = REQUEST_ID,
            topic = "earn.example",
            name = "Earn",
            url = "earn.example",
            source = ConfirmContext.Dapp.Source.Injected,
        ),
    )

    @Test
    fun `the request is readable while its answer is awaited`() = runTest {
        val pending = repository.expect(REQUEST_ID, request)

        assertSame(request, repository.pendingRequest(REQUEST_ID))

        val outcome = async { pending.await() }
        repository.approve(REQUEST_ID, "0xsigned")

        assertEquals(EvmProviderRepository.Outcome.Signed(REQUEST_ID, "0xsigned"), outcome.await())
        assertNull(repository.pendingRequest(REQUEST_ID))
    }

    @Test
    fun `a rejected request is gone`() = runTest {
        val pending = repository.expect(REQUEST_ID, request)

        val outcome = async { pending.await() }
        repository.reject(REQUEST_ID)

        assertEquals(EvmProviderRepository.Outcome.Rejected(REQUEST_ID), outcome.await())
        assertNull(repository.pendingRequest(REQUEST_ID))
    }

    @Test
    fun `a failed request is answered as failed, not rejected`() = runTest {
        val pending = repository.expect(REQUEST_ID, request)

        val outcome = async { pending.await() }
        repository.fail(REQUEST_ID)

        assertEquals(EvmProviderRepository.Outcome.Failed(REQUEST_ID), outcome.await())
        assertNull(repository.pendingRequest(REQUEST_ID))
    }

    @Test
    fun `a rejected request is gone before its waiter wakes up`() {
        repository.expect(REQUEST_ID, request)

        repository.reject(REQUEST_ID)

        assertNull(repository.pendingRequest(REQUEST_ID))
    }

    @Test
    fun `an approved request is gone before its waiter wakes up`() {
        repository.expect(REQUEST_ID, request)

        repository.approve(REQUEST_ID, "0xsigned")

        assertNull(repository.pendingRequest(REQUEST_ID))
    }

    @Test
    fun `an unknown id has no request`() {
        assertNull(repository.pendingRequest(REQUEST_ID))
    }

    @Test
    fun `a pending request is not gone`() = runTest {
        repository.expect(REQUEST_ID, request)

        val gone = async { repository.requestGone(REQUEST_ID).first() }
        runCurrent()

        assertFalse(gone.isCompleted)
        gone.cancel()
    }

    @Test
    fun `a request is gone once rejected`() = runTest {
        repository.expect(REQUEST_ID, request)
        val gone = async { repository.requestGone(REQUEST_ID).first() }
        runCurrent()

        repository.reject(REQUEST_ID)
        runCurrent()

        assertTrue(gone.isCompleted)
    }

    @Test
    fun `a request is gone once approved`() = runTest {
        repository.expect(REQUEST_ID, request)
        val gone = async { repository.requestGone(REQUEST_ID).first() }
        runCurrent()

        repository.approve(REQUEST_ID, "0xsigned")
        runCurrent()

        assertTrue(gone.isCompleted)
    }

    @Test
    fun `a request is gone once its waiter is cancelled`() = runTest {
        val pending = repository.expect(REQUEST_ID, request)
        val waiter = launch { pending.await() }
        val gone = async { repository.requestGone(REQUEST_ID).first() }
        runCurrent()
        assertFalse(gone.isCompleted)

        waiter.cancel()
        runCurrent()

        assertTrue(gone.isCompleted)
        assertNull(repository.pendingRequest(REQUEST_ID))
    }

    @Test
    fun `an unknown id is gone at once`() = runTest {
        val gone = async { repository.requestGone(REQUEST_ID).first() }
        runCurrent()

        assertTrue(gone.isCompleted)
    }

    private companion object {
        const val REQUEST_ID = "confirm-1"
    }
}
