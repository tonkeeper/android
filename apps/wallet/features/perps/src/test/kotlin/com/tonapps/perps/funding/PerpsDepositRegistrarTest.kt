package com.tonapps.perps.funding

import com.tonapps.wallet.data.multichain.perps.PerpsDepositPendingStep
import com.tonapps.wallet.data.multichain.perps.PerpsDepositRoute
import com.tonapps.wallet.data.multichain.perps.PerpsDepositStage
import com.tonapps.wallet.data.multichain.perps.PerpsFundingKind
import com.tonapps.wallet.data.multichain.perps.PerpsFundingRecord
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerpsDepositRegistrarTest {

    private val env = PerpsFundingTestEnv()
    private val registrar = PerpsDepositRegistrar(env.repository, env.journal)

    @Test
    fun `polling keeps the step the backend reports`() = runTest {
        journal()
        answerDeposit(status = "pending", step = "converting")

        val converting = registrar.refresh(current())
        assertEquals(PerpsDepositPendingStep.CONVERTING, converting.pendingStep)
        assertEquals(PerpsDepositStage.REGISTERED_PENDING, converting.stage)

        answerDeposit(status = "pending", step = null)
        val unchanged = registrar.refresh(current())
        assertEquals(PerpsDepositPendingStep.CONVERTING, unchanged.pendingStep)

        answerDeposit(status = "pending", step = "adding")
        assertEquals(PerpsDepositPendingStep.ADDING, registrar.refresh(current()).pendingStep)
    }

    @Test
    fun `a step outside pending is not taken`() = runTest {
        journal()
        answerDeposit(status = "credited", step = "adding", credited = "9.5")

        val credited = registrar.refresh(current())

        assertEquals(PerpsDepositStage.CREDITED, credited.stage)
        assertNull(credited.pendingStep)
        assertEquals("9.5", credited.creditedAmount)
    }

    @Test
    fun `a step this build does not know keeps the last one`() = runTest {
        journal(step = PerpsDepositPendingStep.SENDING)
        answerDeposit(status = "pending", step = "bridging")

        assertEquals(PerpsDepositPendingStep.SENDING, registrar.refresh(current()).pendingStep)
    }

    @Test
    fun `the backend's L1 transaction is journaled so history finds the deposit`() = runTest {
        journal()
        answerDeposit(status = "pending", step = "adding", l1TxHash = "0x1E5FF4")

        val adding = registrar.refresh(current())

        assertEquals("0x1e5ff4", adding.l1TxHash)
        assertEquals(true, adding.toDepositStatus()?.transactions?.contains(perpsTransactionHash("lighter:0x1e5ff4")))
    }

    @Test
    fun `an opened outcome stays acknowledged in the journal`() = runTest {
        journal()
        answerDeposit(status = "refunded", step = null)
        registrar.refresh(current())

        env.journal.acknowledgeDepositStatuses(setOf(OPERATION_ID))

        assertEquals(true, current().toDepositStatus()?.acknowledged)
        assertEquals(PerpsDepositStage.REFUNDED, current().stage)
    }

    @Test
    fun `a cross-chain deposit names its source transaction`() = runTest {
        journal(stage = PerpsDepositStage.REGISTRATION_PENDING, depositId = null, sourceTxHash = " $SOURCE_TX_HASH ")
        answerOpen()

        registrar.register(current())

        val body = openBody()
        assertEquals(SOURCE_TX_HASH, body.string("source_tx_hash"))
        assertEquals("execution", body.string("execution_id"))
    }

    @Test
    fun `a relayed deposit without a hash registers without one`() = runTest {
        journal(stage = PerpsDepositStage.REGISTRATION_PENDING, depositId = null)
        answerOpen()

        val registered = registrar.register(current())

        assertTrue(openBody().namesNoSourceTransaction())
        assertEquals(PerpsDepositStage.REGISTERED_PENDING, registered.stage)
    }

    private suspend fun current(): PerpsFundingRecord = env.journal.list(WALLET_ID).single()

    private fun answerOpen() {
        env.answer("POST /funding/deposit") {
            env.ok(
                buildJsonObject {
                    put("deposit_id", DEPOSIT_ID)
                    put("status", "pending")
                },
            )
        }
    }

    private fun openBody(): JsonObject = checkNotNull(env.requests.single { it.endpoint == "POST /funding/deposit" }.body)

    private fun JsonObject.namesNoSourceTransaction(): Boolean = get("source_tx_hash")?.let { it is JsonNull } ?: true

    private fun answerDeposit(status: String, step: String?, credited: String? = null, l1TxHash: String? = null) {
        env.answer("GET /funding/deposit/$DEPOSIT_ID") {
            env.ok(
                buildJsonObject {
                    put("deposit_id", DEPOSIT_ID)
                    put("status", status)
                    step?.let { put("pending_step", it) }
                    credited?.let { put("credited_amount", it) }
                    l1TxHash?.let { put("l1_tx_hash", it) }
                },
            )
        }
    }

    private suspend fun journal(
        step: PerpsDepositPendingStep? = null,
        stage: PerpsDepositStage = PerpsDepositStage.REGISTERED_PENDING,
        sourceTxHash: String? = null,
        depositId: String? = DEPOSIT_ID,
    ) {
        env.journal.saveIfAbsent(
            PerpsFundingRecord(
                operationId = OPERATION_ID,
                walletId = WALLET_ID,
                kind = PerpsFundingKind.DEPOSIT,
                stage = stage,
                route = PerpsDepositRoute.CROSSCHAIN_SWAP,
                beneficiary = "0x0000000000000000000000000000000000000001",
                sourceAssetId = "ton/mainnet/coin",
                sourceSymbol = "GRAM",
                sourceDecimals = 9,
                sourceAmount = "10000000000",
                targetAmount = "9500000",
                quoteId = null,
                executionId = "execution",
                sourceTxHash = sourceTxHash,
                l1TxHash = null,
                steps = emptyList(),
                linkSignature = null,
                linkDeadline = null,
                registration = null,
                depositId = depositId,
                creditedAmount = null,
                accountIndex = null,
                failureReason = null,
                pendingStep = step,
                estimatedDurationSeconds = 146,
                revision = 0,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
    }

    private companion object {
        const val WALLET_ID = "wallet"
        const val OPERATION_ID = "operation"
        const val DEPOSIT_ID = "deposit"
        const val SOURCE_TX_HASH = "0xAbCd5ff4e8929153921fc8a02f71673b3cab15404ca1a06e86989f5d59f98c6e"
    }
}
