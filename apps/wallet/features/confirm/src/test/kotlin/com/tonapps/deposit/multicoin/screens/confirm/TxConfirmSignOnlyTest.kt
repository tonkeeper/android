package com.tonapps.deposit.multicoin.screens.confirm

import androidx.lifecycle.ViewModelStore
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmAction
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.signing.SigSet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.transaction.GasReserveResult
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.SignatureGate
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeBuilder
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeState
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.ledger.signing.LedgerSigningInteractor
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.wallet.CredentialIds
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletType
import com.tonapps.wallet.data.passcode.WalletUnlocker
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.testing.testWallet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class TxConfirmSignOnlyTest : BaseUnitTest() {

    private val scheduler get() = dispatchers.scheduler

    private val accountRepo = mockk<McAccountRepository>()
    private val provider = mockk<ChainKitProvider>()
    private val feeBuilder = mockk<TxFeeBuilder>()
    private val walletUnlocker = mockk<WalletUnlocker>()
    private val ledgerSigner = mockk<LedgerSigningInteractor>()
    private val confirmResults = mockk<ConfirmResultListener>(relaxed = true)
    private val store = ViewModelStore()
    private val collection by lazy { CoroutineScope(dispatchers.main) }

    private val eth by lazy { testAccount(TestAssets.eth, balance = BALANCE.toString()) }

    @Before
    fun setUp() {
        coEvery { accountRepo.findAccount(any(), any(), any(), any()) } returns eth
        coEvery { provider.estimateFee(any<Transaction>()) } returns ChainRes.ok(NATIVE_FEE)
        coEvery { provider.getNonce(any()) } returns NodeRes.ok(BigInteger.ONE)
        coEvery { provider.calculateGasReserve(any(), any(), any(), any(), any(), any()) } answers {
            NodeRes.ok(GasReserveResult(amount = thirdArg(), isAmountAdjusted = false, error = null))
        }
        coEvery { provider.signTransaction(any(), any(), any(), any()) } returns SignRes.ok(SigSet(SOFTWARE_SIGNING_OUTPUT))
        every { provider.signedTransactionHex(Chain.Ethereum.Mainnet, SOFTWARE_SIGNING_OUTPUT) } returns SOFTWARE_SIGNED_TX
        every { provider.signedTransactionHex(Chain.Ethereum.Mainnet, LEDGER_SIGNING_OUTPUT) } returns LEDGER_SIGNED_TX
        coEvery { ledgerSigner.signTransactions(any(), any()) } returns listOf(LEDGER_SIGNING_OUTPUT)
        coEvery { feeBuilder.build(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            TxFeeState(energy = secondArg(), estimated = arg(6), options = emptyList())
        }
        coEvery { walletUnlocker.confirm(any()) } returns true
        coEvery { walletUnlocker.unlockWallet(any(), any()) } returns mockk()
    }

    @After
    fun tearDown() {
        collection.cancel()
        store.clear()
    }

    @Test
    fun aLedgerSignatureIsAnsweredAsTheSignedTransaction() {
        coEvery { accountRepo.getWallet(any()) } returns McWalletEntity(
            credentialId = CredentialIds.hardwareDescriptor(TestWallet.ID),
            name = "Ledger",
            id = TestWallet.ID,
            type = McWalletType.Multicoin,
        )

        val feature = signOnly(SignatureGate { false })

        assertNull(feature.confirmationError.value)
        coVerify(exactly = 1) { confirmResults.approved(DAPP, LEDGER_SIGNED_TX) }
        coVerify(exactly = 0) { provider.sendTransaction(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun aSoftwareSignatureIsAnsweredAsTheSignedTransaction() {
        coEvery { accountRepo.getWallet(any()) } returns testWallet()

        val feature = signOnly(SignatureGate { false })

        assertNull(feature.confirmationError.value)
        coVerify(exactly = 1) { confirmResults.approved(DAPP, SOFTWARE_SIGNED_TX) }
        coVerify(exactly = 0) { provider.sendTransaction(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun aWalletWhose2faTurnedSigningOffIsRefusedBeforeThePasscode() {
        coEvery { accountRepo.getWallet(any()) } returns testWallet()

        val feature = signOnly(SignatureGate { true })

        assertEquals(ConfirmationError.SignatureDisabled, feature.confirmationError.value)
        coVerify(exactly = 0) { walletUnlocker.unlockWallet(any(), any()) }
        coVerify(exactly = 0) { provider.signTransaction(any(), any(), any(), any()) }

        store.clear()
        coVerify(timeout = 2_000) { confirmResults.failed(DAPP) }
        coVerify(exactly = 0) { confirmResults.rejected(any()) }
    }

    private fun signOnly(signatureGate: SignatureGate): TxConfirmFeature {
        val feature = TxConfirmFeature(
            request = ConfirmRequest(
                data = CommonTransactionData(assetId = TestAssets.eth.id, walletId = TestWallet.ID),
                type = ConfirmType.Transfer(
                    operation = ConfirmOperations.transfer(
                        chain = Chain.Ethereum.Mainnet,
                        to = TestWallet.ETH_ADDRESS,
                        amount = AMOUNT,
                    ),
                ),
                action = ConfirmAction.Sign,
                context = DAPP,
            ),
            provider = provider,
            accountRepo = accountRepo,
            postTransactionRefreshSchedule = mockk(relaxed = true),
            walletUnlocker = walletUnlocker,
            signatureGate = signatureGate,
            confirmResults = confirmResults,
            feeBuilder = feeBuilder,
            gaslessSender = mockk(relaxed = true),
            settingsRepository = mockk(relaxed = true),
            ledgerSigner = ledgerSigner,
        )
        store.put("tx-confirm", feature)

        val shown = CopyOnWriteArrayList<PendingTransaction?>()
        collection.launch { feature.pendingTx.collect { shown += it } }
        scheduler.runCurrent()

        feature.sendTransaction(requireNotNull(shown.lastOrNull { it != null }) {
            "nothing was prepared; error: ${feature.confirmationError.value}"
        })
        scheduler.advanceTimeBy(SETTLE_MS)
        scheduler.runCurrent()
        return feature
    }

    private companion object {
        const val SETTLE_MS = 10_000L
        val BALANCE: BigInteger = BigInteger.parseString("1000000000000000000")
        val AMOUNT: BigInteger = BigInteger.parseString("1000000000000")
        val NATIVE_FEE: Fee = Fee.Value(TestAssets.eth.value.chain.toAsset(), BigInteger.parseString("21000000000000"))
        val SOFTWARE_SIGNING_OUTPUT = byteArrayOf(1, 2, 3)
        val LEDGER_SIGNING_OUTPUT = byteArrayOf(4, 5, 6)
        const val SOFTWARE_SIGNED_TX = "0x02f86a01"
        const val LEDGER_SIGNED_TX = "0x02f86a02"
        val DAPP = ConfirmContext.Dapp(
            requestId = "request-1",
            topic = "topic-1",
            name = "Uniswap",
            url = "app.uniswap.org",
        )
    }
}
