package com.tonapps.tonkeeper.ui.screen.wallet.manage.list.holder

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.ui.screen.wallet.manage.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentBlueColor
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.iconSecondaryColor
import com.tonapps.uikit.color.stateList
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.api.entity.Blockchain
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.localization.Localization
import uikit.extensions.drawable
import uikit.extensions.withDefaultBadge
import uikit.widget.FrescoView

@SuppressLint("ClickableViewAccessibility")
class TokenHolder(
    parent: ViewGroup,
    private val doOnPinChange: (tokenAddress: String, pin: Boolean) -> Unit,
    private val doOnHiddeChange: (tokenAddress: String, hidden: Boolean) -> Unit,
    private val doOnDrag: (holder: TokenHolder) -> Unit
): Holder<Item.Token>(parent, R.layout.view_wallet_manage_token) {

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val networkIconView = findViewById<FrescoView>(R.id.network_icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val balanceView = findViewById<AppCompatTextView>(R.id.balance)
    private val pinnedView = findViewById<AppCompatImageView>(R.id.pinned)
    private val hiddenView = findViewById<AppCompatImageView>(R.id.hidden)
    private val reorderView = findViewById<AppCompatImageView>(R.id.reorder)

    init {
        reorderView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                doOnDrag(this@TokenHolder)
            }
            false
        }
    }

    override fun onBind(item: Item.Token) {
        itemView.background = item.position.drawable(context)
        iconView.setImageURI(item.iconUri, this)
        titleView.text = if (item.isUSDT) {
            item.symbol.withDefaultBadge(context, Localization.ton)
        } else if (item.isTRC20) {
            item.symbol.withDefaultBadge(context, Localization.trc20)
        } else {
            item.symbol
        }
        if (item.verified) {
            balanceView.setTextColor(context.textSecondaryColor)
            balanceView.text = if (item.hiddenBalance) {
                HIDDEN_BALANCE
            } else {
                item.balanceFormat.withCustomSymbol(context)
            }
        } else if (item.blacklist) {
            balanceView.text = getString(Localization.fake)
            balanceView.setTextColor(context.textSecondaryColor)
        } else {
            balanceView.text = getString(Localization.unverified_token)
            balanceView.setTextColor(context.accentOrangeColor)
        }

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

        val showNetwork = item.isUSDT || item.isTRC20

        networkIconView.visibility = if (showNetwork) View.VISIBLE else View.GONE
        setNetworkIcon(item.blockchain)
    }

    private fun setNetworkIcon(blockchain: Blockchain) {
        val icon = when (blockchain) {
            Blockchain.TON -> R.drawable.ic_ton
            Blockchain.TRON -> R.drawable.ic_tron
        }

        networkIconView.setLocalRes(icon)
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