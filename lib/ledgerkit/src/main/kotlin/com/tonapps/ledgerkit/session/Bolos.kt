package com.tonapps.ledgerkit.session

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.apdu.Apdu
import com.tonapps.ledgerkit.apdu.ApduResponse
import com.tonapps.ledgerkit.apdu.LedgerOperation
import com.tonapps.ledgerkit.apdu.StatusWord
import com.tonapps.ledgerkit.apdu.StatusWordMapper

object Bolos {

    private const val CLA_OS = 0xb0
    private const val CLA_APP = 0xe0
    private const val INS_APP_AND_VERSION = 0x01
    private const val INS_QUIT_APP = 0xa7
    private const val INS_OPEN_APP = 0xd8
    private const val FORMAT_ID = 0x01

    fun appAndVersionCommand(): ByteArray = Apdu.build(CLA_OS, INS_APP_AND_VERSION, 0, 0)

    fun quitAppCommand(): ByteArray = Apdu.build(CLA_OS, INS_QUIT_APP, 0, 0)

    fun openAppCommand(app: LedgerAppId): ByteArray =
        Apdu.build(CLA_APP, INS_OPEN_APP, 0, 0, app.openName.toByteArray(Charsets.US_ASCII))

    fun isAppAndVersion(response: ApduResponse): Boolean = response.data.size >= 3 && response.data[0].toInt() == FORMAT_ID

    fun parseAppAndVersion(response: ApduResponse): LedgerAppInfo {
        if (!response.isOk) {
            throw LedgerFailureException(StatusWordMapper.map(response.statusWord, null, LedgerOperation.AppInfo))
        }
        val data = response.data
        if (data.size < 3 || data[0].toInt() != FORMAT_ID) {
            throw LedgerFailureException(LedgerFailure.Unexpected("Unexpected app info format"))
        }
        var offset = 1
        val nameLength = data[offset++].toInt() and 0xff
        if (offset + nameLength >= data.size) {
            throw LedgerFailureException(LedgerFailure.Unexpected("Truncated app name"))
        }
        val name = String(data, offset, nameLength, Charsets.US_ASCII)
        offset += nameLength
        val versionLength = data[offset++].toInt() and 0xff
        if (offset + versionLength > data.size) {
            throw LedgerFailureException(LedgerFailure.Unexpected("Truncated app version"))
        }
        val version = String(data, offset, versionLength, Charsets.US_ASCII)
        return LedgerAppInfo(name, version)
    }

    fun openAppFailure(statusWord: Int, app: LedgerAppId): LedgerFailure = when (statusWord) {
        StatusWord.APP_NOT_FOUND -> LedgerFailure.AppNotInstalled(app)
        StatusWord.CONDITIONS_NOT_SATISFIED, StatusWord.ACTION_REFUSED -> LedgerFailure.UserRefused(statusWord)
        StatusWord.DEVICE_LOCKED -> LedgerFailure.DeviceLocked
        else -> StatusWordMapper.map(statusWord, app, LedgerOperation.OpenApp)
    }
}
