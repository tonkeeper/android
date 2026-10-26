package com.tonapps.tonkeeper.ui.screen.root

import android.net.Uri
import com.tonapps.blockchain.model.ConfirmInitiator
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.core.deeplink.DeepLink
import com.tonapps.icu.Coins
import com.tonapps.ledger.ton.LedgerConnectData
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import org.ton.cell.Cell
import org.ton.kotlin.crypto.PublicKeyEd25519

sealed class RootEvent {
    data class OpenTab(
        val link: Uri,
        val wallet: WalletEntity,
        val from: String,
        val utm: UtmParameters,
    ): RootEvent()

    data class Swap(
        val wallet: WalletEntity,
        val uri: Uri,
        val address: String,
        val from: String,
        val to: String?,
        val initiator: ConfirmInitiator,
        val utm: UtmParameters,
    ): RootEvent()

    data class Singer(
        val publicKey: PublicKeyEd25519,
        val name: String?,
        val qr: Boolean
    ): RootEvent()

    data class Ledger(
        val connectData: LedgerConnectData,
        val accounts: List<AccountItem>
    ): RootEvent()

    data class Transfer(
        val wallet: WalletEntity,
        val address: String,
        val amount: Coins?,
        val text: String?,
        val jettonAddress: String?,
        val bin: Cell?,
        val initStateBase64: String?,
        val validUnit: Long?,
        val source: DeepLink.Source,
        val utm: UtmParameters,
    ): RootEvent()

    data object CloseCurrentTonConnect: RootEvent()

    data class OpenDAppByShortcut(
        val wallet: WalletEntity,
        val url: Uri,
    ): RootEvent()

    data object CheckGooglePlayUpdate: RootEvent()
}
