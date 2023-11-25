package com.tonkeeper.fragment.wallet.main.list.holder

import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatTextView
import com.facebook.drawee.drawable.ScalingUtils
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.common.ResizeOptions
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.tonkeeper.R
import com.tonkeeper.fragment.jetton.JettonScreen
import com.tonkeeper.fragment.wallet.main.list.item.WalletItem
import com.tonkeeper.fragment.wallet.main.list.item.WalletJettonCellItem

class WalletCellJettonHolder(
    parent: ViewGroup
): WalletCellHolder<WalletJettonCellItem>(parent, R.layout.view_cell_jetton) {

    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val iconView = findViewById<SimpleDraweeView>(R.id.icon)
    private val rateView = findViewById<TextView>(R.id.rate)
    private val balanceView = findViewById<TextView>(R.id.balance)
    private val balanceCurrencyView = findViewById<TextView>(R.id.balance_currency)

    init {
        iconView.hierarchy.actualImageScaleType = ScalingUtils.ScaleType.CENTER_CROP
        // iconView.hierarchy.setPlaceholderImage(R.drawable.bg_token_placeholder)
    }

    override fun onBind(item: WalletJettonCellItem) {
        itemView.setOnClickListener {
            nav?.add(JettonScreen.newInstance(item.address, item.name))
        }
        loadIcon(item.iconURI)
        titleView.text = item.code
        rateView.text = createRate(item.rate, item.rateDiff24h)
        balanceView.text = item.balance
        balanceCurrencyView.text = item.balanceCurrency
    }

    private fun loadIcon(uri: Uri) {
        val builder = ImageRequestBuilder.newBuilderWithSource(uri)
        builder.resizeOptions = ResizeOptions.forSquareSize(102)
        iconView.setImageRequest(builder.build())
    }

    private fun createRate(rate: String, diff24h: String): SpannableString {
        val span = SpannableString("$rate $diff24h")
        span.setSpan(
            ForegroundColorSpan(getDiffColor(diff24h)),
            rate.length,
            rate.length + diff24h.length + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return span
    }

    @ColorInt
    private fun getDiffColor(diff: String): Int {
        val resId = when {
            diff.startsWith("-") -> uikit.R.color.accentRed
            diff.startsWith("+") -> uikit.R.color.accentGreen
            else -> uikit.R.color.textSecondary
        }
        return context.getColor(resId)
    }
}