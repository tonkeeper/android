package com.tonapps.tonkeeper.ui.screen.token.picker.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.ui.screen.token.picker.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.entity.Blockchain
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.localization.Localization
import uikit.extensions.drawable
import uikit.extensions.withDefaultBadge
import uikit.widget.FrescoView

class TokenHolder(
    parent: ViewGroup,
    private val onClick: (Item.Token) -> Unit,
): Holder<Item.Token>(parent, R.layout.view_token) {

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val networkIconView = findViewById<FrescoView>(R.id.network_icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val balanceView = findViewById<AppCompatTextView>(R.id.balance)
    private val checkView = findViewById<View>(R.id.check)

    override fun onBind(item: Item.Token) {
        itemView.setOnClickListener { onClick(item) }
        itemView.background = item.position.drawable(context)
        iconView.setImageURI(item.iconUri, this)

        networkIconView.visibility = if (item.showNetwork) View.VISIBLE else View.GONE
        setNetworkIcon(item.blockchain)

        titleView.text = if (item.showNetwork && item.isTrc20) {
            item.symbol.withDefaultBadge(context, Localization.trc20)
        } else if (item.showNetwork && item.isUsdt) {
            item.symbol.withDefaultBadge(context, Localization.ton)
        } else {
            item.symbol
        }
        balanceView.text = if (item.hiddenBalance) HIDDEN_BALANCE else item.balance.withCustomSymbol(context)
        checkView.visibility = if (item.selected) View.VISIBLE else View.GONE
    }

    private fun setNetworkIcon(blockchain: Blockchain) {
        val icon = when (blockchain) {
            Blockchain.TON -> R.drawable.ic_ton
            Blockchain.TRON -> R.drawable.ic_tron
        }

        networkIconView.setLocalRes(icon)
    }

}