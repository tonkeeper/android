package com.tonapps.tonkeeper.ui.screen.wallet.manage.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.wallet.manage.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentBlueColor
import com.tonapps.uikit.color.iconPrimaryColor
import com.tonapps.uikit.color.iconSecondaryColor
import com.tonapps.uikit.color.stateList
import com.tonapps.uikit.icon.UIKitIcon
import uikit.extensions.drawable
import uikit.widget.FrescoView

class TokenHolder(
    parent: ViewGroup,
    private val doOnPinChange: (tokenAddress: String, pin: Boolean) -> Unit,
    private val doOnHiddeChange: (tokenAddress: String, hidden: Boolean) -> Unit
): Holder<Item.Token>(parent, R.layout.view_wallet_manage_token) {

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val balanceView = findViewById<AppCompatTextView>(R.id.balance)
    private val pinnedView = findViewById<AppCompatImageView>(R.id.pinned)
    private val hiddenView = findViewById<AppCompatImageView>(R.id.hidden)
    private val reorderView = findViewById<AppCompatImageView>(R.id.reorder)

    override fun onBind(item: Item.Token) {
        itemView.background = item.position.drawable(context)
        iconView.setImageURI(item.iconUri, this)
        titleView.text = item.symbol
        balanceView.text = item.balanceFormat

        pinnedView.setOnClickListener {
            val pinned = !item.pinned
            doOnPinChange(item.address, pinned)
            applyPinnedState(pinned)
        }
        applyPinnedState(item.pinned)

        hiddenView.setOnClickListener {
            val hidden = !item.hidden
            doOnHiddeChange(item.address, hidden)
            applyHiddenState(hidden, item.pinned)
        }
        applyHiddenState(item.hidden, item.pinned)
    }

    private fun applyPinnedState(pinned: Boolean) {
        pinnedView.imageTintList = if (pinned) {
            context.accentBlueColor.stateList
        } else {
            context.iconSecondaryColor.stateList
        }

        if (pinned) {
            reorderView.visibility = View.VISIBLE
            hiddenView.visibility = View.GONE
            pinnedView.visibility = View.VISIBLE
        } else {
            reorderView.visibility = View.GONE
            hiddenView.visibility = View.VISIBLE
        }
    }

    private fun applyHiddenState(hidden: Boolean, pinned: Boolean) {
        if (hidden) {
            hiddenView.imageTintList = context.iconSecondaryColor.stateList
            hiddenView.setImageResource(UIKitIcon.ic_eye_closed_outline_28)
            pinnedView.visibility = View.GONE
        } else {
            hiddenView.imageTintList = context.accentBlueColor.stateList
            hiddenView.setImageResource(UIKitIcon.ic_eye_outline_28)
            pinnedView.visibility = View.VISIBLE
        }
    }
}