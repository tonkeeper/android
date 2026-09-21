package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.wallet.main.AssetsExpandState
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.drawable
import uikit.widget.AsyncImageView

class MoreAssetsHolder(parent: ViewGroup): Holder<Item.MoreAssets>(parent, R.layout.view_wallet_more_assets) {

    private val icon1View = findViewById<AsyncImageView>(R.id.icon1)
    private val icon2View = findViewById<AsyncImageView>(R.id.icon2)
    private val icon3View = findViewById<AsyncImageView>(R.id.icon3)
    private val iconViews = arrayOf(icon1View, icon2View, icon3View)

    override fun onBind(item: Item.MoreAssets) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener {
            AssetsExpandState.expand(item.wallet.id)
        }
        setIcons(item.iconUris)
    }

    private fun setIcons(uris: List<Uri>) {
        for (i in iconViews.indices) {
            setIcon(i, uris.getOrNull(i))
        }
    }

    private fun setIcon(index: Int, uri: Uri?) {
        val view = iconViews[index]
        if (uri == null) {
            view.visibility = View.GONE
        } else {
            view.visibility = View.VISIBLE
            view.setImageURI(uri, null)
        }
    }

}
