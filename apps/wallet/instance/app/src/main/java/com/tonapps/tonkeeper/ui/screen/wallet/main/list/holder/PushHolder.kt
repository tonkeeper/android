package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeperx.R
import uikit.navigation.Navigation
import uikit.widget.FrescoView

class PushHolder(parent: ViewGroup): Holder<Item.Push>(parent, R.layout.view_wallet_push) {

    private val icon1View = findViewById<FrescoView>(R.id.icon1)
    private val icon2View = findViewById<FrescoView>(R.id.icon2)
    private val icon3View = findViewById<FrescoView>(R.id.icon3)
    private val iconViews = arrayOf(icon1View, icon2View, icon3View)

    private val textView = findViewById<AppCompatTextView>(R.id.text)

    init {
        itemView.setOnClickListener {
            Navigation.from(context)?.openURL("tonkeeper://activity")
        }
    }

    override fun onBind(item: Item.Push) {
        textView.text = item.text
        setIcons(item.iconUris)
    }

    private fun setIcons(uri: List<Uri>) {
        for (i in iconViews.indices) {
            setIcon(i, uri.getOrNull(i))
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