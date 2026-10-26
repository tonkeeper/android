package com.tonapps.perps.trade

import com.tonapps.chainkit.lighter.LighterConstants
import com.tonapps.chainkit.lighter.model.L2ApproveIntegratorTxInfo
import com.tonapps.chainkit.lighter.model.LighterApproveIntegratorTxReq
import com.tonapps.chainkit.lighter.trading.PerpsExecutionException
import com.tonapps.chainkit.perpetual.PerpsScope
import com.tonapps.perps.data.PerpsSendReason
import com.tonapps.perps.funding.PerpsFundingTestEnv
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.ACCOUNT_INDEX
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GET_NEXT_NONCE
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.HTTP_BAD_REQUEST
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.SEND_TRANSACTIONS
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.WALLET_ID
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import com.tonapps.wallet.data.multichain.perps.PerpsOperationJournal
import com.tonapps.wallet.testing.BaseUnitTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.perpsapi.models.Integrator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class PerpsIntegratorApprovalTest : BaseUnitTest() {

    private lateinit var env: PerpsFundingTestEnv
    private lateinit var relay: PerpsExecutionRelay
    private lateinit var approval: PerpsIntegratorApproval

    private val perpetualRepository = mockk<PerpetualRepository>()
    private val signedApprovalNonces = mutableListOf<Long>()
    private var venueNonce = FIRST_NONCE

    @Before
    fun setUp() {
        env = PerpsFundingTestEnv()
        env.answer(GET_NEXT_NONCE) {
            env.ok(
                buildJsonObject {
                    put("account_index", ACCOUNT_INDEX)
                    put("nonce", venueNonce)
                },
            )
        }
        relay = PerpsExecutionRelay(env.repository, mockk<PerpsOperationJournal>(), PerpsNonceCoordinator())
        approval = PerpsIntegratorApproval(perpetualRepository, relay)
        coEvery {
            perpetualRepository.approveIntegratorTransaction(WALLET_ID, any<LighterApproveIntegratorTxReq>(), any(), any())
        } answers {
            val nonce = thirdArg<Long>()
            signedApprovalNonces += nonce
            signedApproval(nonce)
        }
    }

    @Test
    fun anOrderRefusedForAnUnapprovedIntegratorIsNamedSo() = runTest {
        env.answer(SEND_TRANSACTIONS) {
            env.refused(HTTP_BAD_REQUEST, code = "validation_error", reason = "integrator_not_approved")
        }

        val failure = try {
            relay.submitTransaction(WALLET_ID, TX_TYPE_CREATE_ORDER, "{}", "0x01")
            fail("the refusal was not raised")
            return@runTest
        } catch (e: PerpsExecutionException) {
            e.failure()
        }

        val refused = failure as PerpsTradeFailure.SendRefused
        assertEquals(PerpsSendReason.INTEGRATOR_NOT_APPROVED, refused.reason)
        assertEquals("validation_error", refused.code)
        assertEquals(failure, failure.tag().toTradeFailure())
    }

    @Test
    fun aRenewalApprovesAgainAgainstTheVenueNonce() = runTest {
        assertNull(approval.ensure(WALLET_ID, SCOPE, INTEGRATOR))
        venueNonce++
        assertNull(approval.ensure(WALLET_ID, SCOPE, INTEGRATOR))
        assertEquals(listOf(FIRST_NONCE), signedApprovalNonces)

        val refusedOrderNonce = relay.nextNonce(SCOPE).value
        assertEquals(FIRST_NONCE + 1, refusedOrderNonce)

        assertNull(approval.renew(WALLET_ID, SCOPE, INTEGRATOR))

        assertEquals(listOf(FIRST_NONCE, FIRST_NONCE + 1), signedApprovalNonces)
        venueNonce++
        assertEquals(FIRST_NONCE + 2, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun aRenewalTheVenueRefusesFails() = runTest {
        assertNull(approval.ensure(WALLET_ID, SCOPE, INTEGRATOR))
        env.answer(SEND_TRANSACTIONS) {
            env.refused(HTTP_BAD_REQUEST, code = "validation_error", reason = "lighter_rejected")
        }

        val failure = approval.renew(WALLET_ID, SCOPE, INTEGRATOR) as PerpsTradeFailure.SendRefused

        assertEquals(PerpsSendReason.LIGHTER_REJECTED, failure.reason)
        assertEquals(listOf(FIRST_NONCE, FIRST_NONCE), signedApprovalNonces)
    }

    private fun signedApproval(nonce: Long): L2ApproveIntegratorTxInfo {
        val hash = "0x" + nonce.toString().padStart(HASH_HEX_LENGTH, '0')
        return mockk {
            every { txType } returns LighterConstants.TxTypeL2ApproveIntegrator
            every { getTxHash() } returns hash
            every { getTxInfo() } returns buildJsonObject {
                put("Nonce", nonce)
                put("Hash", hash)
            }.toString()
        }
    }

    private companion object {
        const val FIRST_NONCE = 41L
        const val TX_TYPE_CREATE_ORDER = 14
        const val HASH_HEX_LENGTH = 80

        val SCOPE = PerpsScope(
            walletId = WALLET_ID,
            venue = "lighter",
            chainId = 304,
            environment = "mainnet",
            accountIndex = ACCOUNT_INDEX,
            keyBindingId = "binding",
            expectedPublicKey = "0xkey",
        )

        val INTEGRATOR = Integrator(
            integratorAccountIndex = 281_474_976_710_000L,
            integratorTakerFee = 900,
            integratorMakerFee = 0,
            maxPerpsTakerFee = 1_000,
            maxPerpsMakerFee = 1_000,
            maxSpotTakerFee = 0,
            maxSpotMakerFee = 0,
            approvalTtlMs = 86_400_000L,
        )
    }
}
