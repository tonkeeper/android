package com.tonkeeper.ui.list.wallet.holder

import android.net.Uri
import android.view.ViewGroup
import android.widget.TextView
import com.facebook.drawee.drawable.ScalingUtils
import com.facebook.drawee.generic.RoundingParams
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.common.ResizeOptions
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.tonkeeper.R
import com.tonkeeper.extensions.getDimension
import com.tonkeeper.ui.list.wallet.item.WalletNftItem

class WalletNftHolder(
    parent: ViewGroup
): WalletHolder<WalletNftItem>(parent, R.layout.view_nft) {

    private val radius = context.getDimension(R.dimen.radius)

    private val previewView = findViewById<SimpleDraweeView>(R.id.preview)
    private val titleView = findViewById<TextView>(R.id.title)
    private val descriptionView = findViewById<TextView>(R.id.description)

    init {
        previewView.hierarchy.actualImageScaleType = ScalingUtils.ScaleType.CENTER_CROP
        previewView.hierarchy.roundingParams = RoundingParams.fromCornersRadii(radius, radius, 0f, 0f)
    }

    override fun onBind(item: WalletNftItem) {
        loadImage(item.imageURI)
        titleView.text = item.title
        descriptionView.text = item.description
    }

    private fun loadImage(uri: Uri) {
        val builder = ImageRequestBuilder.newBuilderWithSource(uri)
        builder.resizeOptions = ResizeOptions.forSquareSize(256)
        previewView.setImageRequest(builder.build())
    }
}