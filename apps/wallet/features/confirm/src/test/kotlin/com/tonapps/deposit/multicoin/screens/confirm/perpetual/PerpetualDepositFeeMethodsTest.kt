package com.tonapps.deposit.multicoin.screens.confirm.perpetual

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.deposit.multicoin.screens.confirm.fee.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeBuilder
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeState
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PerpetualDepositFeeMethodsTest : BaseUnitTest() {

    private val position = testAccount(TestAssets.usdtEth, balance = "100000000")
    private val energy = testAccount(TestAssets.eth, balance = "0", price = "2000")
    private val estimated = Fee.Value(TestAssets.eth.value, BigInteger.fromInt(1_000))
    private val battery = TxFee(
        account = FeeAccount.Keeper(balance = BigInteger.fromInt(10), rate = null),
        fee = Fee.Value(Asset.Battery(Chain.Ethereum.Mainnet), BigInteger.fromInt(2)),
    )
    private val shortBattery = battery.copy(account = FeeAccount.Keeper(balance = BigInteger.ONE, rate = null))
    private val token = TxFee(
        account = FeeAccount.Chain(position),
        fee = Fee.Value(TestAssets.usdtEth.value, BigInteger.fromInt(50_000)),
    )
    private val native = TxFee(account = FeeAccount.Chain(energy), fee = estimated)
    private val built = TxFeeState(
        energy = energy,
        estimated = estimated,
        options = listOf(battery, token, native),
        shortBattery = shortBattery,
    )
    private val feeBuilder = mockk<TxFeeBuilder> {
        coEvery { buildOperation(any(), any(), any(), any(), any(), any()) } returns built
    }

    @Test
    fun `the battery is left out while its flag is off`() = runTest {
        val state = methods(batteryFeeEnabled = false).build(depositRequest(), position, energy, estimated)

        assertEquals(listOf(token, native), state.options)
        assertNull(state.shortBattery)
    }

    @Test
    fun `the battery is offered once its flag is on`() = runTest {
        val state = methods(batteryFeeEnabled = true).build(depositRequest(), position, energy, estimated)

        assertEquals(built, state)
    }

    @Test
    fun `a direct gateway deposit is paid with the chain's coin only`() = runTest {
        val state = methods(batteryFeeEnabled = true).build(depositRequest(crossChain = false), position, energy, estimated)

        assertEquals(TxFeeState(energy = energy, estimated = estimated), state)
        coVerify(exactly = 0) { feeBuilder.buildOperation(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a payment no relayer may carry is paid with the chain's coin only`() = runTest {
        val state = methods(batteryFeeEnabled = true).build(depositRequest(payableByRelayer = false), position, energy, estimated)

        assertEquals(TxFeeState(energy = energy, estimated = estimated, options = listOf(native)), state)
        coVerify(exactly = 0) { feeBuilder.buildOperation(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a first deposit's payment keeps whether a relayer may carry it`() {
        val preview = depositRequest(preview = true, paymentOperation = ConfirmOperations.build(listOf(gatewayCall(index = 7))), payableByRelayer = false)

        val payment = methods(batteryFeeEnabled = true).payment(preview) ?: error("no payment")

        assertFalse((payment.type as ConfirmType.PerpetualDeposit).payableByRelayer)
    }

    @Test
    fun `a first deposit is priced on its payment, sent as is`() {
        val paymentOperation = ConfirmOperations.build(listOf(gatewayCall(index = 7)))
        val preview = depositRequest(preview = true, paymentOperation = paymentOperation)

        val payment = methods(batteryFeeEnabled = true).payment(preview) ?: error("no payment")

        val deposit = payment.type as ConfirmType.PerpetualDeposit
        assertFalse(payment.data.preview)
        assertEquals(paymentOperation, deposit.operation)
        assertNull(deposit.paymentOperation)
        assertEquals(preview.data.expiresAt, payment.data.expiresAt)
        assertNull(methods(batteryFeeEnabled = true).payment(depositRequest()))
    }

    private fun methods(batteryFeeEnabled: Boolean) = PerpetualDepositFeeMethods(
        feeBuilder = feeBuilder,
        settingsRepository = mockk(),
        batteryRepository = mockk(),
        isBatteryFeeEnabled = { batteryFeeEnabled },
    )

    private fun depositRequest(
        crossChain: Boolean = true,
        preview: Boolean = false,
        paymentOperation: Operation? = null,
        payableByRelayer: Boolean = true,
    ) = ConfirmRequest(
        data = CommonTransactionData(
            assetId = TestAssets.usdtEth.id,
            walletId = TestWallet.ID,
            expiresAt = EXPIRES_AT,
            preview = preview,
        ),
        type = ConfirmType.PerpetualDeposit(
            operation = ConfirmOperations.build(listOf(gatewayCall(index = 0))),
            amount = BigInteger.fromInt(5_000_000),
            quote = ConfirmType.PerpetualDeposit.Quote(
                id = "quote-1",
                executionId = null,
                beneficiary = GATEWAY,
                depositAmount = BigInteger.fromInt(5_000_000),
                crossChain = crossChain,
            ),
            paymentOperation = paymentOperation,
            payableByRelayer = payableByRelayer,
        ),
    )

    private fun gatewayCall(index: Int) = ConfirmOperations.payload(
        index = index,
        chain = Chain.Ethereum.Mainnet,
        meta = PayloadMeta.Evm.Main(
            to = Address.force(GATEWAY, Chain.Ethereum.Mainnet),
            value = BigInteger.ZERO,
            data = "0x",
        ),
    )

    private companion object {
        const val GATEWAY = "0x3b4d794a66304f130a4db8f2551b0070dfcf5ca7"
        const val EXPIRES_AT = 1_800_000_000_000L
    }
}
