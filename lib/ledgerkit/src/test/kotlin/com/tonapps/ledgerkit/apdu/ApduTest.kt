package com.tonapps.ledgerkit.apdu

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerAppSetting
import com.tonapps.ledgerkit.LedgerFailure
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ApduTest {

    @Test
    fun `apdu header and data`() {
        assertArrayEquals(
            byteArrayOf(0xe0.toByte(), 0x04, 0x80.toByte(), 0x00, 0x02, 0x0a, 0x0b),
            Apdu.build(0xe0, 0x04, 0x80, 0x00, byteArrayOf(0x0a, 0x0b)),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `apdu longer than 255 bytes is rejected`() {
        Apdu.build(0xe0, 0x04, 0, 0, ByteArray(256))
    }

    @Test
    fun `response splits data and status word`() {
        val response = Apdu.parseResponse(byteArrayOf(0x01, 0x02, 0x69, 0x85.toByte()))
        assertArrayEquals(byteArrayOf(0x01, 0x02), response.data)
        assertEquals(0x6985, response.statusWord)
    }

    @Test
    fun `bip32 encoding matches ledger format`() {
        assertArrayEquals(
            byteArrayOf(
                5,
                0x80.toByte(), 0, 0, 44,
                0x80.toByte(), 0, 0, 60,
                0x80.toByte(), 0, 0, 3,
                0, 0, 0, 0,
                0, 0, 0, 0,
            ),
            Bip32Path.parse("m/44'/60'/3'/0/0").encode(),
        )
        assertEquals("m/44'/607'/0'/0'/7'/0'", Bip32Path.parse("44h/607H/0'/0'/7'/0'").toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bip32 rejects garbage`() {
        Bip32Path.parse("m/44'/x/0")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bip32 rejects too deep paths`() {
        Bip32Path.parse("m/1/2/3/4/5/6/7/8/9/10/11")
    }

    @Test
    fun `status words map to failures`() {
        assertEquals(LedgerFailure.DeviceLocked, StatusWordMapper.map(0x5515, LedgerAppId.Ethereum, LedgerOperation.SignTransaction))
        assertEquals(LedgerFailure.UserRefused(0x6985), StatusWordMapper.map(0x6985, LedgerAppId.Ethereum, LedgerOperation.SignTransaction))
        assertEquals(LedgerFailure.InvalidPayload(0x6a80, "Invalid data"), StatusWordMapper.map(0x6a80, LedgerAppId.Ethereum, LedgerOperation.SignTransaction))
        assertEquals(
            LedgerFailure.SettingRequired(LedgerAppId.Ton, LedgerAppSetting.BlindSigning),
            StatusWordMapper.map(0xbd00, LedgerAppId.Ton, LedgerOperation.SignTransaction),
        )
        assertEquals(
            LedgerFailure.SettingRequired(LedgerAppId.Tron, LedgerAppSetting.DataAllowed),
            StatusWordMapper.map(0x6a8b, LedgerAppId.Tron, LedgerOperation.SignTransaction),
        )
        assertEquals(
            LedgerFailure.SettingRequired(LedgerAppId.Tron, LedgerAppSetting.SignByHash),
            StatusWordMapper.map(0x6a8c, LedgerAppId.Tron, LedgerOperation.SignMessage),
        )
        assertEquals(
            LedgerFailure.SettingRequired(LedgerAppId.Tron, LedgerAppSetting.CustomContracts),
            StatusWordMapper.map(0x6a8d, LedgerAppId.Tron, LedgerOperation.SignTransaction),
        )
        assertEquals(LedgerFailure.DeviceError(0x6a8d), StatusWordMapper.map(0x6a8d, LedgerAppId.Ethereum, LedgerOperation.SignTransaction))
        assertEquals(LedgerFailure.AppNotInstalled(LedgerAppId.Tron), StatusWordMapper.map(0x6807, LedgerAppId.Tron, LedgerOperation.OpenApp))
        assertEquals(LedgerFailure.WrongAppOpen("", LedgerAppId.Ethereum), StatusWordMapper.map(0x6d00, LedgerAppId.Ethereum, LedgerOperation.Address))
        assertEquals(LedgerFailure.DeviceError(0x6f01), StatusWordMapper.map(0x6f01, LedgerAppId.Ethereum, LedgerOperation.SignTransaction))
    }
}
