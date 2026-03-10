package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.extensions.isLocal
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.extensions.buildRateString
import com.tonapps.tonkeeper.ui.screen.token.viewer.TokenScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.api.entity.value.Blockchain
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.localization.Localization
import uikit.extensions.drawable
import uikit.extensions.withDefaultBadge
import uikit.widget.AsyncImageView
import uikit.widget.ResizeOptions

class TokenHolder(parent: ViewGroup): Holder<Item.Token>(parent, R.layout.view_cell_jetton) {

    private val iconView = findViewById<AsyncImageView>(R.id.icon)
    private val networkIconView = findViewById<AsyncImageView>(R.id.network_icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val rateView = findViewById<AppCompatTextView>(R.id.rate)
    private val balanceContainerView = findViewById<View>(R.id.balance_container)
    private val balanceView = findViewById<AppCompatTextView>(R.id.balance)
    private val balanceFiatView = findViewById<AppCompatTextView>(R.id.balance_currency)
    private val openButtonView = findViewById<View>(R.id.button_open)

    override fun onBind(item: Item.Token) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener {
            openToken(item)
        }
        if (item.blacklist) {
            titleView.text = getString(Localization.fake)
            iconView.clear(null)
        } else {
            val text = if (item.showNetwork && item.isUSDT) {
                item.symbol.withDefaultBadge(context, Localization.ton)
            } else if (item.showNetwork && item.isTRC20) {
                item.symbol.withDefaultBadge(context, Localization.trc20)
            } else {
                item.symbol
            }
            titleView.text = text
            setTokenIcon(item.iconUri)

            networkIconView.visibility = if (item.showNetwork) View.VISIBLE else View.GONE
            setNetworkIcon(item.blockchain)
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

        if (item.isUSDe && item.balance.isZero) {
            balanceContainerView.visibility = View.GONE
            openButtonView.visibility = View.VISIBLE
            openButtonView.setOnClickListener {
                openToken(item)
            }
        } else {
            balanceContainerView.visibility = View.VISIBLE
            openButtonView.visibility = View.GONE
        }
    }

    private fun setNetworkIcon(blockchain: Blockchain) {
        val icon = when (blockchain) {
            Blockchain.TON -> R.drawable.ic_ton
            Blockchain.TRON -> R.drawable.ic_tron
        }

        networkIconView.setLocalRes(icon)
    }

    private fun setTokenIcon(uri: Uri) {
        if (uri.isLocal) {
            iconView.setImageURI(uri, null)
        } else {
            iconView.setImageURIWithResize(uri, ResizeOptions.forSquareSize(256))
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

    private fun openToken(item: Item.Token) {
        navigation?.add(TokenScreen.newInstance(item.wallet, item.address, item.name, item.symbol))
    }

}