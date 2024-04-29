package com.tonapps.tonkeeper.ui.screen.events.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.events.list.Item
import com.tonapps.tonkeeper.ui.screen.nft.NftScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.resolveColor
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import uikit.extensions.drawable
import uikit.navigation.Navigation
import uikit.widget.FrescoView
import uikit.widget.LoaderView

class ActionHolder(parent: ViewGroup): Holder<Item.Action>(parent, R.layout.view_history_action) {

    private val loaderView = findViewById<LoaderView>(R.id.loader)
    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val subtitleView = findViewById<AppCompatTextView>(R.id.subtitle)
    private val amountView = findViewById<AppCompatTextView>(R.id.amount)
    private val amount2View = findViewById<AppCompatTextView>(R.id.amount2)

    private val bodyView = findViewById<View>(R.id.body)

    private val commentView = findViewById<AppCompatTextView>(R.id.comment)
    private val nftView = findViewById<View>(R.id.nft)
    private val nftIconView = findViewById<FrescoView>(R.id.nft_icon)
    private val nftNameView = findViewById<AppCompatTextView>(R.id.nft_name)
    private val nftCollectionView = findViewById<AppCompatTextView>(R.id.nft_collection)

    init {
        amount2View.visibility = View.GONE
    }

    override fun onBind(item: Item.Action) {
        itemView.background = item.position.drawable(context)
        iconView.setImageURI(item.iconUri, null)
        loaderView.visibility = if (item.loading) View.VISIBLE else View.GONE

        titleView.setText(item.titleRes)
        subtitleView.text = item.subtitle
        setComment(item.comment)
        if (item.nft == null && item.comment.isNullOrBlank()) {
            bodyView.visibility = View.GONE
        } else {
            bodyView.visibility = View.VISIBLE
            setValue(item.value, item.valueColorRef)
            setNft(item.nft)
        }
    }

    private fun setComment(comment: String?) {
        if (comment.isNullOrBlank()) {
            commentView.visibility = View.GONE
        } else {
            commentView.visibility = View.VISIBLE
            commentView.text = comment
        }
    }

    private fun setValue(value: CharSequence?, colorRef: Int) {
        if (value.isNullOrBlank()) {
            amountView.visibility = View.GONE
        } else {
            amountView.visibility = View.VISIBLE
            amountView.text = value
            amountView.setTextColor(context.resolveColor(colorRef))
        }
    }

    private fun setNft(nft: NftEntity?) {
        if (nft == null) {
            nftView.visibility = View.GONE
        } else {
            nftView.visibility = View.VISIBLE
            nftView.setOnClickListener {
                Navigation.from(context)?.add(NftScreen.newInstance(nft))
            }
            nftIconView.setImageURI(nft.thumbUri, null)
            nftNameView.text = nft.name
            nftCollectionView.text = nft.collectionName
        }
    }
}