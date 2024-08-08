package com.tonapps.tonkeeper.ui.screen.ledger.steps

import com.tonapps.ledger.ton.LedgerConnectData
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import org.ton.cell.Cell

sealed class LedgerEvent {
    data class Ready(val isReady: Boolean): LedgerEvent()
    data class Loading(val loading: Boolean): LedgerEvent()
    data class Error(val message: String): LedgerEvent()
    data class Next(val connectData: LedgerConnectData, val accounts: List<AccountItem>): LedgerEvent()
    data class SignedTransaction(val body: Cell): LedgerEvent()
    data class SignedProof(val proof: ByteArray): LedgerEvent()
    data object Rejected: LedgerEvent()
    data class WrongVersion(val requiredVersion: String): LedgerEvent()
}
