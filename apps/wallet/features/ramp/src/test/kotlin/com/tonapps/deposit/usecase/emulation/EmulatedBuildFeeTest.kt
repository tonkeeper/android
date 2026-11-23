package com.tonapps.deposit.usecase.emulation

import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.deposit.screens.send.state.SendFee
import com.tonapps.deposit.usecase.emulation.Emulated.Companion.buildFee
import com.tonapps.icu.Coins
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.battery.entity.BatteryConfigEntity
import com.tonapps.wallet.data.core.entity.TransferType
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.rates.entity.RatesEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.tonapi.models.MessageConsequences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatedBuildFeeTest {

    private val wallet = mockk<WalletEntity> {
        every { network } returns TonNetwork.MAINNET
    }

    private val batteryRepository = mockk<BatteryRepository> {
        coEvery { getConfig(TonNetwork.MAINNET) } returns BatteryConfigEntity.Empty.copy(
            excessesAccount = EXCESSES_ACCOUNT,
            chargeCost = CHARGE_COST,
        )
        coEvery { getCharges(wallet, ignoreCache = false) } returns 120
    }

    private val tonRates = mockk<RatesEntity> {
        every { convertTON(any()) } returns Coins.of(0.01)
    }

    private val ratesRepository = mockk<RatesRepository> {
        coEvery { getTONRates(any(), any()) } returns tonRates
    }

    @Test
    fun `a battery fee counts the emulated extra in charges against the battery's own balance`() = runTest {
        val fee = batteryEmulated(extra = -5_200_000L).buildFee(
            wallet = wallet,
            api = mockk(),
            accountRepository = mockk(),
            batteryRepository = batteryRepository,
            ratesRepository = ratesRepository,
        )

        assertTrue(fee is SendFee.Battery)
        fee as SendFee.Battery
        // 0.0052 TON at 0.0026 TON a charge.
        assertEquals(2, fee.charges)
        assertEquals(120, fee.chargesBalance)
        assertEquals(-5_200_000L, fee.extra)
    }

    private fun batteryEmulated(extra: Long) = Emulated(
        consequences = mockk<MessageConsequences> {
            every { event.extra } returns extra
        },
        type = TransferType.Battery,
        total = Emulated.Total(Coins.ZERO, 0, false),
        extra = Emulated.defaultExtra,
        currency = WalletCurrency.USD,
    )

    private companion object {
        const val CHARGE_COST = "0.0026"
        const val EXCESSES_ACCOUNT = "0:7ae5056c3fd9406f9bbbe7c7089cd4c40801d9075486cbedb7ce12df119eacf1"
    }
}
