package com.tonapps.tonkeeper.ui.screen.dns.renew.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.extensions.max12
import com.tonapps.extensions.short12
import com.tonapps.tonkeeper.ui.screen.dns.renew.list.Item
import com.tonapps.tonkeeper.ui.screen.nft.NftScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.UIKitColor
import com.tonapps.uikit.color.resolveColor
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.localization.Localization
import uikit.extensions.drawable
import uikit.extensions.withDefaultBadge
import uikit.navigation.Navigation

class Holder(parent: ViewGroup): BaseListHolder<Item>(parent, R.layout.view_domain_renew) {

    private val navigation = Navigation.from(context)
    private val nameView = findViewById<AppCompatTextView>(R.id.name)
    private val detailsView = findViewById<AppCompatTextView>(R.id.details)

    override fun onBind(item: Item) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener {
            item.nft?.let { openNft(item.wallet, it) }
        }
        nameView.text = if (item.inSale) {
            item.name.max12.withDefaultBadge(context, Localization.on_sale)
        } else {
            item.name
        }

        detailsView.text = context.getString(Localization.renew_dns_expires, item.daysUntilExpiration)
        if (7 >= item.daysUntilExpiration) {
            detailsView.setTextColor(context.resolveColor(UIKitColor.accentRedColor))
        } else {
            detailsView.setTextColor(context.resolveColor(UIKitColor.textSecondaryColor))
        }
    }

    private fun openNft(wallet: WalletEntity, nft: NftEntity) {
        navigation?.add(NftScreen.newInstance(wallet, nft))
    }

}