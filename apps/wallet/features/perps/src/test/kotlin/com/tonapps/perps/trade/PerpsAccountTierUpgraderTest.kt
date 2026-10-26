package com.tonapps.perps.trade

import com.tonapps.core.helper.EnvironmentHelper
import com.tonapps.perps.data.PerpsCandleFeed
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.core.PerpsAPI
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import com.tonapps.wallet.data.multichain.lighter.StoredLighterAccount
import com.tonapps.wallet.testing.BaseUnitTest
import io.infrastructure.ClientError
import io.infrastructure.ClientException
import io.infrastructure.ServerException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.perpsapi.apis.AccountApi
import io.perpsapi.models.AccountTier
import io.perpsapi.models.ChangeAccountTierRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class PerpsAccountTierUpgraderTest : BaseUnitTest() {

    private val accountApi = mockk<AccountApi>()
    private val perpetualRepository = mockk<PerpetualRepository>()
    private val repository = PerpsRepository(
        api = mockk<API> { every { perps } returns mockk<PerpsAPI> { every { account } returns accountApi } },
        candleFeed = mockk<PerpsCandleFeed>(),
        environment = mockk<EnvironmentHelper>(),
    )

    @Before
    fun setUp() {
        coEvery { perpetualRepository.getAccount(WALLET_ID) } returns StoredLighterAccount(ACCOUNT_INDEX, API_KEY_INDEX)
        coEvery { perpetualRepository.authorization(WALLET_ID, any()) } returns AUTHORIZATION
        every { accountApi.changeAccountTier(TO_PLUS, WALLET_ID) } just runs
    }

    @Test
    fun aStandardAccountIsMovedToPlusOnce() = runTest {
        every { accountApi.getAccountTier(WALLET_ID) } returns STANDARD
        val upgrader = upgrader()

        upgrader.ensurePlusAfterActivation(WALLET_ID)
        advanceTimeBy(RECHECK_INTERVAL_MS + 1)
        upgrader.ensurePlusInBackground(WALLET_ID)
        upgrader.ensurePlusAfterActivation(WALLET_ID)
        advanceUntilIdle()

        verify(exactly = 1) { accountApi.getAccountTier(WALLET_ID) }
        verify(exactly = 1) { accountApi.changeAccountTier(TO_PLUS, WALLET_ID) }
    }

    @Test
    fun aFeePayingTierIsLeftAlone() = runTest {
        for (tier in listOf(AccountTier(id = "plus", name = "plus"), AccountTier(id = "prem", name = "premium"))) {
            every { accountApi.getAccountTier(WALLET_ID) } returns tier
            upgrader().ensurePlusInBackground(WALLET_ID)
            advanceUntilIdle()
        }

        verify(exactly = 2) { accountApi.getAccountTier(WALLET_ID) }
        verify(exactly = 0) { accountApi.changeAccountTier(any(), any()) }
    }

    @Test
    fun aTransientFailureIsRetried() = runTest {
        every { accountApi.getAccountTier(WALLET_ID) } returns STANDARD
        every { accountApi.changeAccountTier(TO_PLUS, WALLET_ID) } throws
            IOException("timeout") andThenThrows
            ServerException("unavailable", HTTP_UNAVAILABLE) andThen Unit

        upgrader().ensurePlusInBackground(WALLET_ID)
        advanceUntilIdle()

        verify(exactly = 3) { accountApi.changeAccountTier(TO_PLUS, WALLET_ID) }
    }

    @Test
    fun aRefusalIsNotRetriedUntilTheNextCheck() = runTest {
        every { accountApi.getAccountTier(WALLET_ID) } returns STANDARD
        every { accountApi.changeAccountTier(TO_PLUS, WALLET_ID) } throws
            refusal("tier_change_refused") andThen Unit
        val upgrader = upgrader()

        upgrader.ensurePlusInBackground(WALLET_ID)
        advanceUntilIdle()
        upgrader.ensurePlusInBackground(WALLET_ID)
        advanceUntilIdle()
        verify(exactly = 1) { accountApi.changeAccountTier(TO_PLUS, WALLET_ID) }

        advanceTimeBy(RECHECK_INTERVAL_MS)
        upgrader.ensurePlusInBackground(WALLET_ID)
        advanceUntilIdle()
        verify(exactly = 2) { accountApi.changeAccountTier(TO_PLUS, WALLET_ID) }
    }

    @Test
    fun aMissingReadOnlyTokenWaitsForTheNextCheck() = runTest {
        every { accountApi.getAccountTier(WALLET_ID) } throws refusal("readonly_token_unavailable")

        upgrader().ensurePlusInBackground(WALLET_ID)
        advanceUntilIdle()

        verify(exactly = 1) { accountApi.getAccountTier(WALLET_ID) }
        verify(exactly = 0) { accountApi.changeAccountTier(any(), any()) }
    }

    @Test
    fun activationChecksAgainRightAway() = runTest {
        every { accountApi.getAccountTier(WALLET_ID) } throws
            refusal("readonly_token_unavailable") andThen STANDARD
        val upgrader = upgrader()

        upgrader.ensurePlusInBackground(WALLET_ID)
        advanceUntilIdle()
        upgrader.ensurePlusAfterActivation(WALLET_ID)

        verify(exactly = 1) { accountApi.changeAccountTier(TO_PLUS, WALLET_ID) }
    }

    @Test
    fun activationJoinsTheRunningCheck() = runTest {
        every { accountApi.getAccountTier(WALLET_ID) } returns STANDARD
        coEvery { perpetualRepository.authorization(WALLET_ID, any()) } coAnswers {
            delay(SIGNING_LATENCY_MS)
            AUTHORIZATION
        }
        val upgrader = upgrader()

        upgrader.ensurePlusInBackground(WALLET_ID)
        runCurrent()
        val startedAt = currentTime
        upgrader.ensurePlusAfterActivation(WALLET_ID)

        assertEquals(SIGNING_LATENCY_MS, currentTime - startedAt)
        verify(exactly = 1) { accountApi.getAccountTier(WALLET_ID) }
        verify(exactly = 1) { accountApi.changeAccountTier(TO_PLUS, WALLET_ID) }
    }

    @Test
    fun activationDoesNotWaitForAStuckUpgrade() = runTest {
        every { accountApi.getAccountTier(WALLET_ID) } returns STANDARD
        coEvery { perpetualRepository.authorization(WALLET_ID, any()) } coAnswers { awaitCancellation() }
        val upgrader = upgrader(backgroundScope)

        val startedAt = currentTime
        upgrader.ensurePlusAfterActivation(WALLET_ID)

        assertEquals(ACTIVATION_WAIT_MS, currentTime - startedAt)
    }

    @Test
    fun aTradeWaitsForTheFirstCheck() = runTest {
        every { accountApi.getAccountTier(WALLET_ID) } returns STANDARD

        upgrader().ensurePlusBeforeTrading(WALLET_ID)

        verify(exactly = 1) { accountApi.changeAccountTier(TO_PLUS, WALLET_ID) }
    }

    @Test
    fun aTradeRightAfterAFailedCheckDoesNotWait() = runTest {
        every { accountApi.getAccountTier(WALLET_ID) } throws refusal("readonly_token_unavailable")
        val upgrader = upgrader()
        upgrader.ensurePlusInBackground(WALLET_ID)
        advanceUntilIdle()

        val startedAt = currentTime
        upgrader.ensurePlusBeforeTrading(WALLET_ID)

        assertEquals(0L, currentTime - startedAt)
        verify(exactly = 1) { accountApi.getAccountTier(WALLET_ID) }
    }

    @Test
    fun aLockedKeyStoreDoesNotFailTheActivation() = runTest {
        every { accountApi.getAccountTier(WALLET_ID) } returns STANDARD
        coEvery { perpetualRepository.authorization(WALLET_ID, any()) } throws
            IllegalStateException("Session key is locked") andThen AUTHORIZATION

        upgrader().ensurePlusAfterActivation(WALLET_ID)

        verify(exactly = 1) { accountApi.changeAccountTier(TO_PLUS, WALLET_ID) }
    }

    @Test
    fun aWalletWithoutAnActivatedAccountIsSkipped() = runTest {
        coEvery { perpetualRepository.getAccount(WALLET_ID) } returns null

        upgrader().ensurePlusAfterActivation(WALLET_ID)
        upgrader().ensurePlusInBackground(WALLET_ID)
        advanceUntilIdle()

        verify(exactly = 0) { accountApi.getAccountTier(any()) }
        coVerify(exactly = 0) { perpetualRepository.authorization(any(), any()) }
    }

    private fun TestScope.upgrader(scope: CoroutineScope = this) = PerpsAccountTierUpgrader(
        repository = repository,
        perpetualRepository = perpetualRepository,
        scope = scope,
        nowMs = { RECHECK_INTERVAL_MS + currentTime },
    )

    private fun refusal(code: String) = ClientException(
        message = code,
        statusCode = HTTP_BAD_REQUEST,
        response = ClientError<Any>(
            body = """{"code":"$code","message":"refused","retryable":false}""",
            statusCode = HTTP_BAD_REQUEST,
        ),
    )

    private companion object {
        const val WALLET_ID = "wallet"
        const val ACCOUNT_INDEX = 747_112L
        const val API_KEY_INDEX = 4
        const val AUTHORIZATION = "auth-token"
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAVAILABLE = 503
        const val RECHECK_INTERVAL_MS = 60_000L
        const val ACTIVATION_WAIT_MS = 3_000L
        const val SIGNING_LATENCY_MS = 500L

        val STANDARD = AccountTier(id = "std", name = "standard")
        val TO_PLUS = ChangeAccountTierRequest(lighterAuth = AUTHORIZATION, newTier = "plus")
    }
}
