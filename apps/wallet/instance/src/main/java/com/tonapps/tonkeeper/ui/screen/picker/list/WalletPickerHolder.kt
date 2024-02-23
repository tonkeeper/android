package com.tonapps.tonkeeper.ui.screen.picker.list

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.util.Consumer
import com.tonapps.emoji.ui.EmojiView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.wallet.localization.Localization
import ton.wallet.Wallet
import ton.wallet.WalletType
import uikit.extensions.drawable

class WalletPickerHolder(
    parent: ViewGroup,
    private val onClick: Consumer<Wallet>
): BaseListHolder<WalletPickerItem>(parent, R.layout.view_wallet_item) {

    private val colorView = findViewById<View>(R.id.wallet_color)
    private val emojiView = findViewById<EmojiView>(R.id.wallet_emoji)
    private val nameView = findViewById<AppCompatTextView>(R.id.wallet_name)
    private val balanceView = findViewById<AppCompatTextView>(R.id.wallet_balance)
    private val checkView = findViewById<AppCompatImageView>(R.id.check)
    private val typeView = findViewById<AppCompatTextView>(R.id.wallet_type)

    override fun onBind(item: WalletPickerItem) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener { onClick.accept(item.wallet) }

        colorView.backgroundTintList = ColorStateList.valueOf(item.color)
        emojiView.setEmoji(item.emoji)
        nameView.text = item.name
        balanceView.text = item.balance

        if (item.selected) {
            checkView.setImageResource(UIKitIcon.ic_donemark_otline_28)
        } else {
            checkView.setImageResource(0)
        }
        setType(item.wallet.type)
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
