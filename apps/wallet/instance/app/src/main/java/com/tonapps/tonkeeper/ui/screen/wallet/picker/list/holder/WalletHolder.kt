package com.tonapps.tonkeeper.ui.screen.wallet.picker.list.holder

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.emoji.ui.EmojiView
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.extensions.getWalletBadges
import com.tonapps.tonkeeper.koin.accountRepository
import com.tonapps.tonkeeper.ui.screen.name.edit.EditNameScreen
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import uikit.extensions.drawable

class WalletHolder(
    parent: ViewGroup
): Holder<Item.Wallet>(parent, R.layout.view_wallet_item) {

    private val colorView = findViewById<View>(R.id.wallet_color)
    private val emojiView = findViewById<EmojiView>(R.id.wallet_emoji)
    private val nameView = findViewById<AppCompatTextView>(R.id.wallet_name)
    private val typesView = findViewById<AppCompatTextView>(R.id.wallet_types)
    private val balanceView = findViewById<AppCompatTextView>(R.id.wallet_balance)
    private val checkView = findViewById<AppCompatImageView>(R.id.check)
    private val editView = findViewById<View>(R.id.edit)
    private val pencilView = findViewById<View>(R.id.pencil)

    override fun onBind(item: Item.Wallet) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener {
            if (!item.editMode) {
                context.accountRepository?.safeSetSelectedWallet(item.walletId)
            }
        }

        colorView.backgroundTintList = ColorStateList.valueOf(item.color)
        emojiView.setEmoji(item.emoji)
        nameView.text = item.name
        typesView.text = context.getWalletBadges(item.wallet.type, item.wallet.version)

        if (item.hiddenBalance) {
            balanceView.text = HIDDEN_BALANCE
        } else {
            balanceView.text = item.balance.withCustomSymbol(context)
        }

        if (item.selected) {
            checkView.setImageResource(UIKitIcon.ic_donemark_otline_28)
        } else {
            checkView.setImageResource(0)
        }

        if (item.editMode) {
            editView.visibility = View.VISIBLE
            checkView.visibility = View.GONE
            pencilView.setOnClickListener {
                navigation?.add(EditNameScreen.newInstance(item.wallet))
            }
        } else {
            editView.visibility = View.GONE
            checkView.visibility = View.VISIBLE
        }
    }
}