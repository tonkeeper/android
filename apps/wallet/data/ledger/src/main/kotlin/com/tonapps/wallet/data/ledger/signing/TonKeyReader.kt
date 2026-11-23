package com.tonapps.wallet.data.ledger.signing

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.apdu.Apdu
import com.tonapps.ledgerkit.apdu.ApduChannel
import com.tonapps.ledgerkit.apdu.LedgerOperation
import com.tonapps.ledgerkit.apdu.requireOk
import com.tonapps.ledgerkit.controller.LedgerScope
import com.tonapps.wallet.data.ledger.LedgerDerivation

internal object TonKeyReader {

    private const val CLA = 0xe0
    private const val INS_GET_PUBLIC_KEY = 0x05
    private const val P1_SILENT = 0x00
    private const val P1_DISPLAY = 0x01
    private const val PUBLIC_KEY_LENGTH = 32

    suspend fun readPublicKey(scope: LedgerScope, accountIndex: Int, display: Boolean, expectedAddress: String?): ByteArray {
        scope.ensureApp(LedgerAppId.Ton)
        if (display && expectedAddress != null) {
            scope.report(LedgerProgress.VerifyAddress(LedgerAppId.Ton, expectedAddress))
        }
        val p1 = if (display) {
            P1_DISPLAY
        } else {
            P1_SILENT
        }
        val data = scope.session.exchange(
            apdu = Apdu.build(CLA, INS_GET_PUBLIC_KEY, p1, 0, LedgerDerivation.ton(accountIndex).encode()),
            timeout = if (display) {
                null
            } else {
                ApduChannel.NON_INTERACTIVE_TIMEOUT
            },
        ).requireOk(LedgerAppId.Ton, LedgerOperation.Address)
        if (data.size != PUBLIC_KEY_LENGTH) {
            throw LedgerFailureException(LedgerFailure.Unexpected("Unexpected TON public key length"))
        }
        return data
    }
}
