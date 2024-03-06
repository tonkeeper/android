package com.tonapps.tonkeeper.ui.screen.settings.list.holder

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.emoji.ui.EmojiView
import com.tonapps.tonkeeper.ui.screen.settings.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.WalletType
import com.tonapps.wallet.localization.Localization
import uikit.extensions.drawable

class AccountHolder(
    parent: ViewGroup,
    onClick: ((Item) -> Unit)
): Holder<Item.Account>(parent, R.layout.view_wallet_item, onClick) {

    private val colorView = findViewById<View>(R.id.wallet_color)
    private val emojiView = findViewById<EmojiView>(R.id.wallet_emoji)
    private val nameView = findViewById<AppCompatTextView>(R.id.wallet_name)
    private val balanceView = findViewById<AppCompatTextView>(R.id.wallet_balance)
    private val checkView = findViewById<AppCompatImageView>(R.id.check)
    private val typeView = findViewById<AppCompatTextView>(R.id.wallet_type)

    override fun onBind(item: Item.Account) {
        itemView.setOnClickListener { onClick(item) }
        itemView.background = ListCell.Position.SINGLE.drawable(context)

        colorView.backgroundTintList = ColorStateList.valueOf(item.color)
        emojiView.setEmoji(item.emoji)
        nameView.text = item.title
        balanceView.setText(Localization.customize)
        checkView.setImageResource(UIKitIcon.ic_chevron_right_16)
        setType(item.walletType)
    }

    private fun setType(type: WalletType) {
        if (type == WalletType.Default) {
            typeView.visibility = View.GONE
            return
        }
        typeView.visibility = View.VISIBLE
        val resId = when (type) {
            WalletType.Watch -> Localization.watch_only
            WalletType.Testnet -> Localization.testnet
            WalletType.Signer -> Localization.signer
            else -> throw IllegalArgumentException("Unknown wallet type: $type")
        }
        typeView.setText(resId)
    }
}