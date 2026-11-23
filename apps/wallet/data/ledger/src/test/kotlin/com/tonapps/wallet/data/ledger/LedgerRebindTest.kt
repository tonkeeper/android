package com.tonapps.wallet.data.ledger

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.ledgerkit.apdu.ApduResponse
import com.tonapps.ledgerkit.controller.LedgerController
import com.tonapps.ledgerkit.transport.LedgerModel
import com.tonapps.ledgerkit.transport.usb.LedgerUsbIds
import com.tonapps.wallet.data.ledger.discovery.LedgerDevice
import com.tonapps.wallet.data.ledger.signing.toHexString
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.hardware.HardwareWalletDescriptor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LedgerRebindTest {

    private val tonKey = ByteArray(32) { (it + 1).toByte() }
    private val saved = slot<HardwareWalletDescriptor>()

    private fun repository(deviceId: String, deviceName: String, deviceModel: String) = mockk<McAccountRepository> {
        coEvery { getHardwareDescriptor(WALLET) } returns HardwareWalletDescriptor(
            deviceId = deviceId,
            deviceName = deviceName,
            deviceModel = deviceModel,
            accountIndex = 0,
            derivationPaths = emptyMap(),
        )
        coEvery { getCoinAccounts(WALLET) } returns listOf(
            mockk<AccountEntity> {
                every { chain } returns Chain.Ton.Mainnet
                every { publicKey } returns tonKey.toHexString()
            },
        )
        coEvery { getWallets() } returns emptyList()
        coEvery { updateHardwareDescriptor(WALLET, capture(saved)) } returns Unit
    }

    private suspend fun TestScope.rebind(repository: McAccountRepository, device: LedgerDevice) {
        val transport = FakeLedgerTransport("TON") { ApduResponse(tonKey, 0x9000) }
        val controller = LedgerController(transport, backgroundScope, idleTimeout = 1.seconds, doneHold = 1.milliseconds)
        LedgerWalletInteractorImpl(controller, mockk(), repository, mockk()).rebind(WALLET, device)
    }

    @Test
    fun `pairing a bluetooth ledger again over usb keeps its bluetooth address`() = runTest {
        val repository = repository(BLE_ID, "Stax 1A2B", LedgerModel.Stax.name)
        rebind(repository, LedgerDevice(LedgerUsbIds.deviceId(LedgerModel.Stax), LedgerModel.Stax.productName, LedgerModel.Stax))
        assertEquals(BLE_ID, saved.captured.deviceId)
        assertEquals("Stax 1A2B", saved.captured.deviceName)
        assertEquals(LedgerModel.Stax.name, saved.captured.deviceModel)
    }

    @Test
    fun `pairing a usb ledger again over bluetooth gives it the bluetooth address`() = runTest {
        val repository = repository(LedgerUsbIds.deviceId(LedgerModel.Stax), LedgerModel.Stax.productName, LedgerModel.Stax.name)
        rebind(repository, LedgerDevice(BLE_ID, "Stax 1A2B", LedgerModel.Stax))
        assertEquals(BLE_ID, saved.captured.deviceId)
        assertEquals("Stax 1A2B", saved.captured.deviceName)
    }

    private companion object {
        const val WALLET = "wallet"
        const val BLE_ID = "C1:2B:3C:4D:5E:6F"
    }
}
