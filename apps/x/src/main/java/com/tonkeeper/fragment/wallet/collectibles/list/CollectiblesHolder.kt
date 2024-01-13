package com.tonkeeper.fragment.wallet.collectibles.list

import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.facebook.drawee.generic.RoundingParams
import com.facebook.imagepipeline.common.ResizeOptions
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.tonapps.tonkeeperx.R
import com.tonkeeper.fragment.nft.NftScreen
import uikit.extensions.getDimension
import uikit.extensions.round
import uikit.list.BaseListHolder
import uikit.widget.FrescoView

class CollectiblesHolder(parent: ViewGroup): BaseListHolder<CollectiblesItem>(parent, R.layout.view_collectibles) {

    private val radius = context.getDimension(uikit.R.dimen.cornerMedium)

    private val imageView = findViewById<FrescoView>(R.id.image)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val collectionView = findViewById<AppCompatTextView>(R.id.collection)

    init {
        itemView.foreground = RippleDrawable(ColorStateList.valueOf(context.getColor(uikit.R.color.backgroundHighlighted)), null, null)

        itemView.round(radius.toInt())
        imageView.hierarchy.roundingParams = RoundingParams.fromCornersRadii(radius, radius, 0f, 0f)
    }

    override fun onBind(item: CollectiblesItem) {
        loadImage(item.imageURI)
        itemView.setOnClickListener {
            nav?.add(NftScreen.newInstance(item.nftAddress))
        }
        titleView.text = item.title
        collectionView.text = item.collectionName
    }

    private fun loadImage(uri: Uri) {
        val builder = ImageRequestBuilder.newBuilderWithSource(uri)
        builder.resizeOptions = ResizeOptions.forSquareSize(192)
        imageView.setImageRequest(builder.build())
    }
}