package com.tonapps.ledgerkit.session

import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.apdu.Apdu
import com.tonapps.ledgerkit.apdu.ApduChannel

object LedgerPki {

    const val KEY_USAGE_TRUSTED_NAME = 0x04

    private const val CLA_OS = 0xb0
    private const val INS_LOAD_CERTIFICATE = 0x06
    private const val SIGNATURE_TAG: Byte = 0x15
    private const val MAX_SIGNATURE_LENGTH = 0xff

    suspend fun loadCertificate(channel: ApduChannel, keyUsage: Int, descriptor: ByteArray, signature: ByteArray) {
        val payload = descriptor + byteArrayOf(SIGNATURE_TAG, signature.size.toByte()) + signature
        if (signature.size > MAX_SIGNATURE_LENGTH || payload.size > Apdu.MAX_DATA_LENGTH) {
            throw LedgerFailureException(LedgerFailure.InvalidPayload(0, "The certificate is too long for the device"))
        }
        val response = channel.exchange(Apdu.build(CLA_OS, INS_LOAD_CERTIFICATE, keyUsage, 0, payload), ApduChannel.NON_INTERACTIVE_TIMEOUT)
        if (!response.isOk) {
            throw LedgerFailureException(LedgerFailure.DeviceError(response.statusWord))
        }
    }
}
