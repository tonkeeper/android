package com.tonapps.wallet.features.ledger.common

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.ledgerkit.transport.ble.LedgerBleReadiness
import com.tonapps.wallet.data.ledger.connection.LedgerConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerDisplayTextTest {

    @Test
    fun `the device screen shows the connection status`() {
        assertEquals("Ledger", (null as LedgerConnectionStatus?).displayText)
        assertEquals("USB", LedgerConnectionStatus.UsbConnected.displayText)
        assertEquals("Solana", LedgerConnectionStatus.Connected(LedgerAppId.Solana).displayText)
        assertEquals("Connected", LedgerConnectionStatus.Connected(null).displayText)
        assertEquals("Locked", LedgerConnectionStatus.Busy(LedgerPurpose.Connect, LedgerProgress.WaitingUnlock).displayText)
    }

    @Test
    fun `the intro screen prefers a plugged in ledger over the bluetooth state`() {
        assertEquals("USB", ledgerIntroDisplayText(LedgerBleReadiness.BluetoothOff, hasUsbDevice = true))
        assertEquals("BT off", ledgerIntroDisplayText(LedgerBleReadiness.BluetoothOff, hasUsbDevice = false))
        assertEquals("Searching", ledgerIntroDisplayText(LedgerBleReadiness.Ready, hasUsbDevice = false))
    }
}
