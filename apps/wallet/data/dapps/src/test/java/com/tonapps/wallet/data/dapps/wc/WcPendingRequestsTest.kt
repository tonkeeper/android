package com.tonapps.wallet.data.dapps.wc

import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WcPendingRequestsTest {

    private val requests = WcPendingRequests()

    @Test
    fun `a pending request is not gone`() = runTest {
        requests.put(REQUEST_A, request(REQUEST_A, TOPIC_A))

        val gone = async { requests.gone(REQUEST_A).first() }
        runCurrent()

        assertFalse(gone.isCompleted)
        gone.cancel()
    }

    @Test
    fun `a removed request is gone`() = runTest {
        val request = request(REQUEST_A, TOPIC_A)
        requests.put(REQUEST_A, request)
        val gone = async { requests.gone(REQUEST_A).first() }
        runCurrent()

        val removed = requests.remove(REQUEST_A)
        runCurrent()

        assertSame(request, removed)
        assertTrue(gone.isCompleted)
        assertNull(requests.get(REQUEST_A))
    }

    @Test
    fun `an unknown id is gone at once`() = runTest {
        val gone = async { requests.gone(REQUEST_A).first() }
        runCurrent()

        assertTrue(gone.isCompleted)
    }

    @Test
    fun `an ended session drops only its own requests`() = runTest {
        requests.put(REQUEST_A, request(REQUEST_A, TOPIC_A))
        requests.put(REQUEST_B, request(REQUEST_B, TOPIC_A))
        requests.put(REQUEST_C, request(REQUEST_C, TOPIC_B))
        val goneA = async { requests.gone(REQUEST_A).first() }
        val goneB = async { requests.gone(REQUEST_B).first() }
        val goneC = async { requests.gone(REQUEST_C).first() }
        runCurrent()

        val removed = requests.removeTopic(TOPIC_A)
        runCurrent()

        assertEquals(setOf(REQUEST_A, REQUEST_B), removed.toSet())
        assertTrue(goneA.isCompleted)
        assertTrue(goneB.isCompleted)
        assertFalse(goneC.isCompleted)
        assertNull(requests.get(REQUEST_A))
        assertNull(requests.get(REQUEST_B))
        assertEquals(TOPIC_B, (requests.get(REQUEST_C)?.context as? ConfirmContext.Dapp)?.topic)
        goneC.cancel()
    }

    private fun request(requestId: String, topic: String) = ConfirmRequest(
        data = CommonTransactionData(assetId = "eip155/1/coin", walletId = "wallet"),
        type = ConfirmType.Message("0x68690000", ConfirmType.Message.Type.Personal),
        context = ConfirmContext.Dapp(
            requestId = requestId,
            topic = topic,
            name = "dApp",
            url = "dapp.example",
            source = ConfirmContext.Dapp.Source.WalletConnect,
        ),
    )

    private companion object {
        const val REQUEST_A = "request-a"
        const val REQUEST_B = "request-b"
        const val REQUEST_C = "request-c"
        const val TOPIC_A = "topic-a"
        const val TOPIC_B = "topic-b"
    }
}
