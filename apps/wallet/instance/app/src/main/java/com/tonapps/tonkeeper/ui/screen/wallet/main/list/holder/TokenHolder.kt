package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.facebook.imagepipeline.common.ResizeOptions
import com.facebook.imagepipeline.request.ImageRequest
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.tonapps.extensions.isLocal
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.extensions.buildRateString
import com.tonapps.tonkeeper.ui.screen.token.viewer.TokenScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.localization.Localization
import uikit.extensions.drawable
import uikit.extensions.withDefaultBadge
import uikit.widget.FrescoView

class TokenHolder(parent: ViewGroup): Holder<Item.Token>(parent, R.layout.view_cell_jetton) {

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val rateView = findViewById<AppCompatTextView>(R.id.rate)
    private val balanceView = findViewById<AppCompatTextView>(R.id.balance)
    private val balanceFiatView = findViewById<AppCompatTextView>(R.id.balance_currency)

    override fun onBind(item: Item.Token) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener {
            navigation?.add(TokenScreen.newInstance(item.wallet, item.address, item.name, item.symbol))
        }
        if (item.blacklist) {
            titleView.text = getString(Localization.fake)
            iconView.clear(null)
        } else {
            val text = if (item.isUSDT) {
                item.symbol.withDefaultBadge(context, Localization.ton)
            } else {
                item.symbol
            }
            titleView.text = text
            setTokenIcon(item.iconUri)
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

    private fun setTokenIcon(uri: Uri) {
        if (uri.isLocal) {
            iconView.setImageURI(uri, null)
        } else {
            val builder = ImageRequestBuilder.newBuilderWithSource(uri)
            builder.resizeOptions = ResizeOptions.forSquareSize(256)
            iconView.setImageRequest(builder.build())
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