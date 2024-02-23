package com.tonapps.tonkeeper.fragment.wallet.main.list.holder

import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import com.facebook.drawee.drawable.ScalingUtils
import com.facebook.imagepipeline.common.ResizeOptions
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.tonapps.tonkeeper.extensions.getDiffColor
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.fragment.jetton.JettonScreen
import com.tonapps.tonkeeper.fragment.wallet.main.list.item.WalletJettonCellItem
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.localization.Localization
import uikit.extensions.dp
import uikit.navigation.Navigation
import uikit.widget.FrescoView

class WalletCellJettonHolder(
    parent: ViewGroup
): WalletCellHolder<WalletJettonCellItem>(parent, R.layout.view_cell_jetton) {

    private companion object {
        private val iconSize = 44.dp
    }

    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val rateView = findViewById<TextView>(R.id.rate)
    private val balanceView = findViewById<TextView>(R.id.balance)
    private val balanceCurrencyView = findViewById<TextView>(R.id.balance_currency)

    init {
        iconView.hierarchy.actualImageScaleType = ScalingUtils.ScaleType.CENTER_CROP
    }

    override fun onBind(item: WalletJettonCellItem) {

        itemView.setOnClickListener {
            Navigation.from(context)?.add(JettonScreen.newInstance(item.address, item.name))
        }
        loadIcon(item.iconURI)
        titleView.text = item.code
        balanceView.text = item.balance

        if (item.rate.isNullOrBlank()) {
            rateView.setText(Localization.unverified_token)
            rateView.setTextColor(context.accentOrangeColor)
        } else {
            rateView.text = createRate(item.rate, item.rateDiff24h!!)
            rateView.setTextColor(context.textSecondaryColor)
        }

        balanceCurrencyView.text = item.balanceCurrencyFormat
    }

    private fun loadIcon(uri: Uri) {
        val builder = ImageRequestBuilder.newBuilderWithSource(uri)
        builder.resizeOptions = ResizeOptions.forDimensions(iconSize, iconSize)
        iconView.setImageRequest(builder.build())
    }

    private fun createRate(rate: String, diff24h: String): SpannableString {
        val span = SpannableString("$rate $diff24h")
        span.setSpan(
            ForegroundColorSpan(context.getDiffColor(diff24h)),
            rate.length,
            rate.length + diff24h.length + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return span
    }
}