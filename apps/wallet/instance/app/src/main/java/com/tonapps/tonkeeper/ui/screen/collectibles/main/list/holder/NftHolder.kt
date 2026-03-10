package com.tonapps.tonkeeper.ui.screen.collectibles.main.list.holder

import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.collectibles.main.list.Item
import com.tonapps.tonkeeper.ui.screen.nft.NftScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.backgroundHighlightedColor
import com.tonapps.uikit.color.stateList
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.core.Trust
import com.tonapps.wallet.localization.Localization
import uikit.extensions.getDimension
import uikit.extensions.round
import uikit.navigation.Navigation
import uikit.widget.AsyncImageView
import uikit.widget.ResizeOptions

class NftHolder(parent: ViewGroup): Holder<Item.Nft>(parent, R.layout.view_collectibles) {

    private val radius = context.getDimension(uikit.R.dimen.cornerMedium)

    private val imageView = findViewById<AsyncImageView>(R.id.image)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val collectionView = findViewById<AppCompatTextView>(R.id.collection)
    private val saleBadgeView = findViewById<AppCompatImageView>(R.id.sale_badge)
    private val fireBadgeView = findViewById<AppCompatImageView>(R.id.fire_badge)

    init {
        itemView.foreground = RippleDrawable(context.backgroundHighlightedColor.stateList, null, null)

        itemView.round(radius.toInt())
        imageView.setRoundTop(radius)
    }

    override fun onBind(item: Item.Nft) {
        itemView.setOnClickListener {
            Navigation.from(context)?.add(NftScreen.newInstance(item.wallet, item.entity))
        }
        fireBadgeView.visibility = if (item.expiringDomainSoon) View.VISIBLE else View.GONE
        loadImage(item.imageURI, item.hiddenBalance)
        if (item.hiddenBalance) {
            titleView.text = HIDDEN_BALANCE
        } else {
            titleView.text = item.title
        }
        saleBadgeView.visibility = if (item.sale) View.VISIBLE else View.GONE
        setCollectionName(item.collectionName, item.trust, item.hiddenBalance)
    }

    private fun setCollectionName(
        collectionName: String?,
        trust: Trust,
        hiddenBalance: Boolean
    ) {
        val isTrusted = trust == Trust.whitelist || trust == Trust.graylist
        if (isTrusted) {
            collectionView.setTextColor(context.textSecondaryColor)
        } else {
            collectionView.setTextColor(context.accentOrangeColor)
        }
        if (isTrusted && hiddenBalance) {
            collectionView.text = HIDDEN_BALANCE
        } else if (isTrusted && collectionName.isNullOrBlank()) {
            collectionView.text = getString(Localization.unnamed_collection)
        } else if (isTrusted) {
            collectionView.text = collectionName
        } else {
            collectionView.text = getString(Localization.unverified)
        }
    }

    private fun loadImage(uri: Uri, blur: Boolean) {
        imageView.blur = blur
        imageView.setImageURIWithResize(uri, ResizeOptions.forSquareSize(512))
    }
}