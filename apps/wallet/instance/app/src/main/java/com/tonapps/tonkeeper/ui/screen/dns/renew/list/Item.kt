package com.tonapps.tonkeeper.ui.screen.dns.renew.list

import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.collectibles.entities.DnsExpiringEntity
import com.tonapps.wallet.data.collectibles.entities.NftEntity

data class Item(
    val position: ListCell.Position,
    val wallet: WalletEntity,
    val entity: DnsExpiringEntity,
): BaseListItem() {

    val name: String
        get() = entity.name

    val nft: NftEntity?
        get() = entity.dnsItem

    val inSale: Boolean
        get() = entity.inSale

    val daysUntilExpiration: Int by lazy {
        val currentTime = System.currentTimeMillis() / 1000
        val remainingSeconds = entity.expiringAt - currentTime

        if (remainingSeconds <= 0) {
            0
        } else {
            (remainingSeconds / (24 * 60 * 60)).toInt()
        }
    }

}