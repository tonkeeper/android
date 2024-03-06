package com.tonapps.tonkeeper.ui.screen.wallet.list.holder

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.facebook.imagepipeline.common.ResizeOptions
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.tonapps.tonkeeper.extensions.buildRateString
import com.tonapps.tonkeeper.fragment.jetton.JettonScreen
import com.tonapps.tonkeeper.ui.screen.wallet.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.localization.Localization
import uikit.extensions.drawable
import uikit.navigation.Navigation.Companion.navigation
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
            context.navigation?.add(JettonScreen.newInstance(item.address, item.name))
        }
        setImageUri(item.iconUri)
        titleView.text = item.name
        balanceView.text = item.balanceFormat

        if (item.testnet) {
            rateView.visibility = View.GONE
            balanceFiatView.visibility = View.GONE
        } else {
            balanceFiatView.visibility = View.VISIBLE
            balanceFiatView.text = item.fiatFormat
            setRate(item.rate, item.rateDiff24h, item.verified)
        }
    }

    private fun setImageUri(uri: Uri) {
        if (uri.scheme == "res") {
            iconView.setImageURI(uri, this)
        } else {
            val builder = ImageRequestBuilder.newBuilderWithSource(uri)
            builder.resizeOptions = ResizeOptions.forSquareSize(256)
            iconView.setImageRequest(builder.build())
        }
    }

    private fun setRate(rate: String, rateDiff24h: String, verified: Boolean) {
        rateView.visibility = View.VISIBLE
        if (verified) {
            rateView.text = context.buildRateString(rate, rateDiff24h)
            rateView.setTextColor(context.textSecondaryColor)
        } else {
            rateView.setText(Localization.unverified_token)
            rateView.setTextColor(context.accentOrangeColor)
        }
    }

}