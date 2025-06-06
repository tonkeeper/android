package com.tonapps.tonkeeper.ui.screen.root

import android.net.Uri
import com.tonapps.ledger.ton.LedgerConnectData
import com.tonapps.tonkeeper.core.entities.WalletPurchaseMethodEntity
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.wallet.api.entity.StoryEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.tlb.CellRef

sealed class RootEvent {
    data class OpenTab(
        val link: Uri,
        val wallet: WalletEntity,
        val from: String,
    ): RootEvent()

    data class Swap(
        val wallet: WalletEntity,
        val uri: Uri,
        val address: String,
        val from: String,
        val to: String?
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
        val amount: Long?,
        val text: String?,
        val jettonAddress: String?,
        val bin: Cell?,
        val initStateBase64: String?,
        val validUnit: Long?,
    ): RootEvent()

    data object CloseCurrentTonConnect: RootEvent()

    data class OpenDAppByShortcut(
        val wallet: WalletEntity,
        val url: Uri,
        val source: String
    ): RootEvent()
}