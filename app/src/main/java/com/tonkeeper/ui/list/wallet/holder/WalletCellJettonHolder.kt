package com.tonkeeper.ui.list.wallet.holder

import android.net.Uri
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.facebook.drawee.drawable.ScalingUtils
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.tonkeeper.R
import com.tonkeeper.ui.list.wallet.item.WalletJettonCellItem

class WalletCellJettonHolder(
    parent: ViewGroup
): WalletCellHolder<WalletJettonCellItem>(parent, R.layout.view_cell_jetton) {

    private val iconView = findViewById<SimpleDraweeView>(R.id.icon)
    private val codeView = findViewById<AppCompatTextView>(R.id.code)
    private val amountView = findViewById<AppCompatTextView>(R.id.amount)

    init {
        iconView.hierarchy.actualImageScaleType = ScalingUtils.ScaleType.CENTER_CROP
        iconView.hierarchy.setPlaceholderImage(R.drawable.bg_token_placeholder)
    }

    override fun onBind(item: WalletJettonCellItem) {
        loadIcon(item.iconURI)
        codeView.text = item.code
        amountView.text = item.balance
    }

    private fun loadIcon(uri: Uri) {
        val builder = ImageRequestBuilder.newBuilderWithSource(uri)
        // builder.resizeOptions = ResizeOptions.forSquareSize(72)
        iconView.setImageRequest(builder.build())
    }
}