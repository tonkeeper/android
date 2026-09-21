package com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenFrom
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.extensions.buildRateString
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeper.ui.screen.token.viewer.TokenScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.trading.AssetsFragment
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.localization.Localization
import uikit.extensions.drawable
import uikit.navigation.Navigation
import uikit.widget.AsyncImageView
import uikit.widget.BadgeTextView

class TokenHolder(parent: ViewGroup): Holder<Item.Token>(parent, R.layout.view_cell_jetton) {

    private val iconView = findViewById<AsyncImageView>(R.id.icon)
    private val titleView = findViewById<BadgeTextView>(R.id.title)
    private val rateView = findViewById<AppCompatTextView>(R.id.rate)
    private val balanceView = findViewById<AppCompatTextView>(R.id.balance)
    private val balanceFiatView = findViewById<AppCompatTextView>(R.id.balance_currency)

    init {
        itemView.background = ListCell.Position.SINGLE.drawable(context)
    }

    override fun onBind(item: Item.Token) {
        itemView.setOnClickListener {
            openToken(item)
        }

        if (item.blacklist) {
            titleView.setTextWithBadge(getString(Localization.fake), null)
            iconView.clear(null)
        } else {
            titleView.setTextWithBadge(item.symbol, null)
            iconView.setImageURI(item.iconUri, this)
        }

        balanceView.text = if (item.hiddenBalance) {
            HIDDEN_BALANCE
        } else {
            item.balanceFormat.withCustomSymbol(context)
        }

        if (item.testnet) {
            rateView.visibility = View.GONE
            balanceFiatView.visibility = View.GONE
        } else {
            balanceFiatView.visibility = View.VISIBLE
            if (item.hiddenBalance) {
                balanceFiatView.text = HIDDEN_BALANCE
            } else {
                balanceFiatView.text = item.fiatFormat.withCustomSymbol(context)
            }
            setRate(item.rate, item.rateDiff24h, item.verified)
        }
    }

    private fun openToken(item: Item.Token) {
        val assetId = item.assetId
        if (assetId != null && item.wallet.type == WalletType.Multichain) {
            Navigation.from(context)?.add(
                AssetsFragment.newInstance(
                    assetId = assetId,
                    from = AssetScreenFrom.WalletScreen,
                    name = item.name,
                    imageUrl = item.iconUri.toString(),
                )
            )
            return
        }
        if (item.balance.isPositive) {
            Navigation.from(context)?.add(
                TokenScreen.newInstance(
                    item.wallet,
                    item.address,
                    item.name,
                    item.symbol
                )
            )
        }
    }

    private fun setRate(rate: CharSequence, rateDiff24h: String, verified: Boolean) {
        rateView.visibility = View.VISIBLE
        if (verified) {
            rateView.text = context.buildRateString(rate, rateDiff24h).withCustomSymbol(context)
            rateView.setTextColor(context.textSecondaryColor)
        } else {
            rateView.setText(Localization.unverified_token)
            rateView.setTextColor(context.accentOrangeColor)
        }
    }

}
