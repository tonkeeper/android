package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.UIKitColor
import com.tonapps.uikit.color.constantBlackColor
import com.tonapps.uikit.icon.UIKitIcon
import uikit.extensions.drawable
import uikit.extensions.setRightDrawable
import uikit.navigation.Navigation

class AlertHolder(parent: ViewGroup): Holder<Item.Alert>(parent, R.layout.view_wallet_alert) {

    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val messageView = findViewById<AppCompatTextView>(R.id.message)
    private val actionView = findViewById<AppCompatTextView>(R.id.action)

    init {
        val actionIcon = context.drawable(UIKitIcon.ic_chevron_right_12)
        actionIcon.setTint(context.constantBlackColor)
        actionView.setRightDrawable(actionIcon)
    }

    override fun onBind(item: Item.Alert) {
        titleView.text = item.title
        messageView.text = item.message

        if (item.buttonTitle == null) {
            actionView.visibility = View.GONE
            return
        }

        actionView.visibility = View.VISIBLE
        actionView.text = item.buttonTitle
        if (item.buttonUrl != null) {
            actionView.setOnClickListener {
                Navigation.from(context)?.openURL(item.buttonUrl)
            }
        }
    }
}