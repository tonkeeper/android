package com.tonapps.perps.trade

import com.tonapps.chainkit.CryptoKitClient
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.perpetual.PerpetualAccountKeySource
import com.tonapps.chainkit.perpetual.PerpetualAccountMediator
import com.tonapps.chainkit.perpetual.PerpetualChain
import com.tonapps.chainkit.perpetual.PerpsAccountDelegate
import com.tonapps.chainkit.perpetual.PerpsAccountRegistration
import com.tonapps.chainkit.perpetual.PerpsAccountState
import com.tonapps.core.helper.EnvironmentHelper
import com.tonapps.perps.data.LIGHTER_API_KEY_INDEX
import com.tonapps.perps.data.PerpsCandleFeed
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.core.PerpsAPI
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.lighter.LighterSigningCredentials
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import com.tonapps.wallet.data.multichain.lighter.StoredLighterAccount
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.FakeTimeProvider
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.perpsapi.apis.AccountApi
import io.perpsapi.models.Account
import io.perpsapi.models.AccountAPIKey
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PerpsAccountActivationTest : BaseUnitTest() {

    private val accountApi = mockk<AccountApi>()
    private val perpetualRepository = mockk<PerpetualRepository>(relaxUnitFun = true)
    private val walletKeySource = mockk<PerpetualAccountKeySource> {
        coEvery { deriveTradingKey() } answers { ByteArray(KEY_SIZE) { 1 } }
    }
    private val mediator = mockk<PerpetualAccountMediator> {
        every { keySource(any()) } returns walletKeySource
    }
    private val client = mockk<CryptoKitClient> {
        every { perpetual.getAccountMediator(PerpetualChain.Lighter) } returns mediator
    }
    private val mcAccountRepository = mockk<McAccountRepository> {
        coEvery { getCoinAccounts(WALLET_ID) } returns listOf(testAccount(TestAssets.eth).data)
    }
    private val repository = PerpsRepository(
        api = mockk<API> { every { perps } returns mockk<PerpsAPI> { every { account } returns accountApi } },
        candleFeed = mockk<PerpsCandleFeed>(),
        environment = mockk<EnvironmentHelper>(),
        listing = mockk(relaxed = true),
    )
    private val credentials = mockk<LighterSigningCredentials>()
    private val serverTime = FakeTimeProvider()
    private val saved = mutableListOf<StoredLighterAccount>()
    private val registered = mutableListOf<Long>()

    @Before
    fun setUp() {
        coEvery { perpetualRepository.tradingPublicKey(WALLET_ID) } returns TRADING_PUBLIC_KEY
        coEvery { perpetualRepository.tradingKey(WALLET_ID) } answers { ByteArray(KEY_SIZE) { 1 } }
        coEvery { perpetualRepository.publicKeyOf(any()) } returns TRADING_PUBLIC_KEY
        coEvery { perpetualRepository.saveActivation(WALLET_ID, any(), any()) } answers { saved += secondArg<StoredLighterAccount>() }
        coEvery { perpetualRepository.signingCredentials(WALLET_ID) } answers { credentials.takeIf { saved.isNotEmpty() } }
        coEvery { mediator.activate(any(), any(), any()) } coAnswers { activateAsChainKit(secondArg()) }
    }

    @Test
    fun anOwnAccountHoldingTheKeptKeyIsActivatedWithoutThePasscode() = runTest {
        answerAccounts(account())

        val activation = service().ensureActivated(WALLET_ID) { throw AssertionError("the passcode was asked") }

        assertEquals(PerpsActivation.Ready(credentials), activation)
        assertEquals(listOf(StoredLighterAccount(ACCOUNT_INDEX, LIGHTER_API_KEY_INDEX)), saved)
    }

    @Test
    fun theKeyRegistrationIsSignedOnTheServersClock() = runTest {
        answerAccounts(account())
        serverTime.offsetMs = -DEVICE_CLOCK_SKEW_MS
        val before = serverTime.currentTimeMillis()

        service().ensureActivated(WALLET_ID) { throw AssertionError("the passcode was asked") }

        val signedAt = before..serverTime.currentTimeMillis()
        coVerify { mediator.activate(any(), any(), match { it in signedAt }) }
    }

    @Test
    fun anAccountBoundToAnotherAddressIsNotActivated() = runTest {
        answerAccounts(account(l1Address = FOREIGN_L1_ADDRESS))

        val activation = service().ensureActivated(WALLET_ID) { throw AssertionError("the passcode was asked") }

        assertEquals(PerpsActivation.Failed, activation)
        assertTrue(saved.isEmpty())
        coVerify(exactly = 0) { mediator.activate(any(), any(), any()) }
    }

    @Test
    fun anAccountTheRegistrationReadsBoundToAnotherAddressIsNotSaved() = runTest {
        answerAccounts(account(), account(l1Address = FOREIGN_L1_ADDRESS))

        assertEquals(PerpsActivation.Failed, service().ensureActivated(WALLET_ID) { throw AssertionError() })
        assertTrue(saved.isEmpty())
    }

    @Test
    fun anotherAccountInTheRegistrationReadIsNotSigned() = runTest {
        answerAccounts(account(), account(accountIndex = FOREIGN_ACCOUNT_INDEX))

        assertEquals(PerpsActivation.Failed, service().ensureActivated(WALLET_ID) { throw AssertionError() })
        assertTrue(saved.isEmpty())
        assertTrue(registered.isEmpty())
    }

    @Test
    fun anotherAccountInTheConfirmationIsNotSaved() = runTest {
        answerAccounts(account(keys = emptyList()), account(keys = emptyList()), account(accountIndex = FOREIGN_ACCOUNT_INDEX))

        val activation = service().ensureActivated(WALLET_ID) { mockk<MnemonicWallet>() }

        assertEquals(PerpsActivation.Failed, activation)
        assertEquals(listOf(ACCOUNT_INDEX), registered)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun theAccountTheKeyWasRegisteredForIsSaved() = runTest {
        answerAccounts(account(keys = emptyList()), account(keys = emptyList()), account())

        val activation = service().ensureActivated(WALLET_ID) { mockk<MnemonicWallet>() }

        assertEquals(PerpsActivation.Ready(credentials), activation)
        assertEquals(listOf(ACCOUNT_INDEX), registered)
        assertEquals(listOf(StoredLighterAccount(ACCOUNT_INDEX, LIGHTER_API_KEY_INDEX)), saved)
    }

    @Test
    fun anAddressTheBackendSpellsWithTheChecksumIsTheWallets() = runTest {
        answerAccounts(account(l1Address = TestWallet.ETH_ADDRESS))

        val activation = service().ensureActivated(WALLET_ID) { throw AssertionError("the passcode was asked") }

        assertEquals(PerpsActivation.Ready(credentials), activation)
    }

    private fun TestScope.service() = PerpsAccountService(
        repository = repository,
        perpetualRepository = perpetualRepository,
        client = client,
        mcAccountRepository = mcAccountRepository,
        walletUnlocker = mockk(),
        tierUpgrader = mockk(relaxed = true),
        timeProvider = serverTime,
        scope = backgroundScope,
    )

    private fun answerAccounts(vararg accounts: Account) {
        every { accountApi.getAccount(WALLET_ID) } returnsMany accounts.toList()
    }

    // What ChainKit's mediator does with the delegate: it ends where the account holds the key,
    // and otherwise registers it for the account it read and asks for the confirmation.
    private suspend fun activateAsChainKit(delegate: PerpsAccountDelegate): PerpsAccountState {
        val current = delegate.accountState()
        val accountIndex = current.accountIndex ?: return current
        if (current.hasL2Key == true) {
            return current
        }
        registered += accountIndex
        return delegate.confirmRegistration(
            PerpsAccountRegistration(
                accountIndex = accountIndex,
                apiKeyIndex = LIGHTER_API_KEY_INDEX,
                txType = CHANGE_PUB_KEY_TX_TYPE,
                txInfo = "{}",
                txHash = "0xhash",
            )
        )
    }

    private fun account(
        accountIndex: Long = ACCOUNT_INDEX,
        l1Address: String = TestWallet.ETH_ADDRESS.lowercase(),
        keys: List<AccountAPIKey> = listOf(AccountAPIKey(LIGHTER_API_KEY_INDEX, TRADING_PUBLIC_KEY)),
    ) = Account(
        accountIndex = accountIndex.toInt(),
        l1Address = l1Address,
        hasL2Key = keys.isNotEmpty(),
        hasRoToken = true,
        apiKeys = keys,
    )

    private companion object {
        val WALLET_ID = TestWallet.ID
        const val ACCOUNT_INDEX = 744_540L
        const val FOREIGN_ACCOUNT_INDEX = 747_112L
        const val FOREIGN_L1_ADDRESS = "0x000000000000000000000000000000000000dead"
        val TRADING_PUBLIC_KEY = "0x" + "11".repeat(40)
        const val KEY_SIZE = 40
        const val CHANGE_PUB_KEY_TX_TYPE = 8
        const val DEVICE_CLOCK_SKEW_MS = 60L * 60L * 1_000L
    }
}
