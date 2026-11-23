package com.tonapps.perps.trade

import com.tonapps.core.helper.EnvironmentHelper
import com.tonapps.perps.data.PerpsCandleFeed
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.multichain.perps.PerpsOperationKind
import com.tonapps.wallet.data.multichain.perps.PerpsOperationRecord
import com.tonapps.wallet.data.multichain.perps.PerpsOperationStatus
import com.tonapps.wallet.data.multichain.perps.PerpsStepKind
import com.tonapps.wallet.data.multichain.perps.PerpsStepRecord
import com.tonapps.wallet.data.multichain.perps.PerpsStepStatus
import com.tonapps.wallet.testing.BaseUnitTest
import io.infrastructure.ClientException
import io.mockk.every
import io.mockk.mockk
import io.perpsapi.models.OpenPosition
import io.perpsapi.models.OpenPositionDetail
import io.perpsapi.models.Order
import io.perpsapi.models.OrderDetail
import io.perpsapi.models.OrderStatus
import io.perpsapi.models.PositionSide
import io.perpsapi.models.PositionState
import io.perpsapi.models.PositionStateOrder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerpsVenueWitnessTest : BaseUnitTest() {

    private val api = mockk<API>()
    private val repository = PerpsRepository(api, mockk<PerpsCandleFeed>(), mockk<EnvironmentHelper>(), mockk(relaxed = true))
    private val witness = PerpsVenueWitness(repository)

    @Test
    fun `a position the service no longer returns is no proof of a close`() = runTest {
        noPositionOnService()

        assertNull(witness.closedOnVenue(WALLET_ID, MARKET_INDEX))
    }

    @Test
    fun `a position the service still returns is not closed`() = runTest {
        positionOnService()

        assertEquals(false, witness.closedOnVenue(WALLET_ID, MARKET_INDEX))
    }

    @Test
    fun `a vanished position is not a partial close`() = runTest {
        noPositionOnService()

        assertEquals(
            false,
            witness.movedOnVenue(
                operation(PerpsOperationKind.CLOSE, baseBefore = "0.24"),
                step(PerpsStepKind.CLOSE),
            ),
        )
    }

    @Test
    fun `a cancel the service knows nothing about yet is unknown`() = runTest {
        stateOrders(ORDER_INDEX)
        orderNotFound(ORDER_INDEX)

        assertNull(witness.cancelOutcome(operation(PerpsOperationKind.ORDER_CANCEL), cancelStep(ORDER_INDEX)))
    }

    @Test
    fun `a cancel follows the status of our order`() = runTest {
        stateOrders(ORDER_INDEX)

        orderDetail(ORDER_INDEX, OrderStatus.canceled)
        assertEquals(
            PerpsCancelOnVenue.CANCELLED,
            witness.cancelOutcome(operation(PerpsOperationKind.ORDER_CANCEL), cancelStep(ORDER_INDEX)),
        )

        orderDetail(ORDER_INDEX, OrderStatus.filled)
        assertEquals(
            PerpsCancelOnVenue.FILLED,
            witness.cancelOutcome(operation(PerpsOperationKind.ORDER_CANCEL), cancelStep(ORDER_INDEX)),
        )

        orderDetail(ORDER_INDEX, OrderStatus.open)
        assertEquals(
            PerpsCancelOnVenue.RESTING,
            witness.cancelOutcome(operation(PerpsOperationKind.ORDER_CANCEL), cancelStep(ORDER_INDEX)),
        )

        orderDetail(ORDER_INDEX, OrderStatus.unknown)
        assertNull(witness.cancelOutcome(operation(PerpsOperationKind.ORDER_CANCEL), cancelStep(ORDER_INDEX)))
    }

    @Test
    fun `a cancel keyed by our client index reads the order event first`() = runTest {
        stateOrders(LEG_INDEX, PositionStateOrder(orderIndex = ORDER_INDEX, status = "canceled"))

        assertEquals(
            PerpsCancelOnVenue.CANCELLED,
            witness.cancelOutcome(operation(PerpsOperationKind.ORDER_CANCEL), cancelStep(LEG_INDEX)),
        )
    }

    @Test
    fun `a replaced leg is cancelled only once its own order says so`() = runTest {
        stateOrders(LEG_INDEX)
        orderNotFound(LEG_INDEX)
        assertNull(witness.cancelOutcome(operation(PerpsOperationKind.AUTO_CLOSE), cancelStep(LEG_INDEX)))

        stateOrders(LEG_INDEX, PositionStateOrder(orderIndex = ORDER_INDEX, status = "pending"))
        assertEquals(
            PerpsCancelOnVenue.RESTING,
            witness.cancelOutcome(operation(PerpsOperationKind.AUTO_CLOSE), cancelStep(LEG_INDEX)),
        )

        stateOrders(LEG_INDEX, PositionStateOrder(orderIndex = ORDER_INDEX, status = "filled"))
        assertEquals(
            PerpsCancelOnVenue.FILLED,
            witness.cancelOutcome(operation(PerpsOperationKind.AUTO_CLOSE), cancelStep(LEG_INDEX)),
        )

        stateOrders(LEG_INDEX, PositionStateOrder(orderIndex = ORDER_INDEX, status = "canceled"))
        assertEquals(
            PerpsCancelOnVenue.CANCELLED,
            witness.cancelOutcome(operation(PerpsOperationKind.AUTO_CLOSE), cancelStep(LEG_INDEX)),
        )
    }

    @Test
    fun `an unknown status from the position state falls through to the order itself`() = runTest {
        stateOrders(ORDER_INDEX, PositionStateOrder(orderIndex = ORDER_INDEX, status = "unknown"))
        orderDetail(ORDER_INDEX, OrderStatus.canceled)

        assertEquals(
            PerpsCancelOnVenue.CANCELLED,
            witness.cancelOutcome(operation(PerpsOperationKind.ORDER_CANCEL), cancelStep(ORDER_INDEX)),
        )

        orderDetail(ORDER_INDEX, OrderStatus.unknown)
        assertNull(witness.cancelOutcome(operation(PerpsOperationKind.ORDER_CANCEL), cancelStep(ORDER_INDEX)))
    }

    private fun positionOnService() {
        every { api.perps.positions.getOpenPosition(POSITION_ID, WALLET_ID) } returns OpenPositionDetail(
            position = OpenPosition(id = POSITION_ID, marketIndex = MARKET_INDEX, symbol = "HYPE", side = PositionSide.long),
        )
    }

    private fun noPositionOnService() {
        every { api.perps.positions.getOpenPosition(POSITION_ID, WALLET_ID) } throws
            ClientException("Client error : 404", HTTP_NOT_FOUND)
    }

    private fun stateOrders(index: Long, vararg orders: PositionStateOrder) {
        every { api.perps.positions.getPositionState(POSITION_ID, index, WALLET_ID) } returns PositionState(
            marketIndex = MARKET_INDEX,
            `open` = null,
            lastClosed = null,
            orders = orders.toList(),
        )
    }

    private fun orderDetail(index: Long, status: OrderStatus) {
        every { api.perps.orders.getOrder(ORDER_ID_PREFIX + index, WALLET_ID) } returns
            OrderDetail(order = Order(orderIndex = index, status = status))
    }

    private fun orderNotFound(index: Long) {
        every { api.perps.orders.getOrder(ORDER_ID_PREFIX + index, WALLET_ID) } throws
            ClientException("Client error : 404", HTTP_NOT_FOUND)
    }

    private fun operation(
        kind: PerpsOperationKind,
        baseBefore: String? = null,
    ) = PerpsOperationRecord(
        operationId = OPERATION_ID,
        walletId = WALLET_ID,
        marketIndex = MARKET_INDEX,
        kind = kind,
        side = LONG,
        marginQuote = "1",
        leverage = 20,
        reviewJson = null,
        planDigest = null,
        expectedBaseSize = null,
        baseBefore = baseBefore,
        marginBefore = null,
        status = PerpsOperationStatus.IN_PROGRESS,
        error = null,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun cancelStep(index: Long) = step(PerpsStepKind.ORDER_CANCEL, clientOrderIndex = index)

    private fun step(
        kind: PerpsStepKind,
        clientOrderIndex: Long = CLIENT_ORDER_INDEX,
    ) = PerpsStepRecord(
        operationId = OPERATION_ID,
        stepId = kind.name.lowercase(),
        kind = kind,
        requiresStepId = null,
        attemptId = "attempt",
        apiKeyIndex = 3,
        nonce = 1,
        accountIndex = ACCOUNT_INDEX,
        txType = 14,
        txHash = TX_HASH,
        txInfo = "{}",
        clientOrderIndex = clientOrderIndex,
        planId = null,
        txExpiry = null,
        orderRefs = emptyList(),
        submittedAt = System.currentTimeMillis(),
        status = PerpsStepStatus.SENT,
        error = null,
        updatedAt = 0,
    )

    private companion object {
        const val OPERATION_ID = "op"
        const val WALLET_ID = "wallet"
        const val MARKET_INDEX = 24
        const val POSITION_ID = "lighter:24"
        const val ORDER_ID_PREFIX = "lighter:"
        const val HTTP_NOT_FOUND = 404
        const val ACCOUNT_INDEX = 744540L
        const val CLIENT_ORDER_INDEX = 1793031745412L
        const val ORDER_INDEX = 7318347650989466L
        const val LEG_INDEX = 1793031421273L
        const val TX_HASH = "e14b0f8e9b9b78b6"
        const val LONG = "LONG"
    }
}
