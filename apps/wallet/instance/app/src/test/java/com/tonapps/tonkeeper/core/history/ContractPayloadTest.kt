package com.tonapps.tonkeeper.core.history

import io.tonapi.models.AccountAddress
import io.tonapi.models.SmartContractAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContractPayloadTest {

    private val confirmation = ActionOptions(removeDate = true, showContractPayload = true)

    @Test
    fun `confirmation shows the decoded payload of a contract call`() {
        val payload = "QueryId: 0\nNewSigners:\n  - 0:attacker"

        assertEquals(payload, HistoryHelper.contractPayload(call(payload), confirmation))
    }

    @Test
    fun `surrounding whitespace is trimmed, inner lines are kept`() {
        assertEquals(
            "QueryId: 0\n  Signer: true",
            HistoryHelper.contractPayload(call("\n  QueryId: 0\n  Signer: true \n\n"), confirmation)
        )
    }

    @Test
    fun `a payload tonapi could not decode is not shown`() {
        assertNull(HistoryHelper.contractPayload(call(null), confirmation))
        assertNull(HistoryHelper.contractPayload(call(""), confirmation))
        assertNull(HistoryHelper.contractPayload(call(" \n\t "), confirmation))
    }

    @Test
    fun `history keeps contract calls without the payload`() {
        assertNull(HistoryHelper.contractPayload(call("QueryId: 0"), ActionOptions()))
        assertNull(HistoryHelper.contractPayload(call("QueryId: 0"), ActionOptions(removeDate = true)))
    }

    private fun call(payload: String?) = SmartContractAction(
        executor = account("0:1111111111111111111111111111111111111111111111111111111111111111"),
        contract = account("0:2222222222222222222222222222222222222222222222222222222222222222"),
        gramAttached = 10_000_000L,
        operation = "Call",
        payload = payload,
    )

    private fun account(address: String) = AccountAddress(
        address = address,
        isScam = false,
        isWallet = false,
    )
}
