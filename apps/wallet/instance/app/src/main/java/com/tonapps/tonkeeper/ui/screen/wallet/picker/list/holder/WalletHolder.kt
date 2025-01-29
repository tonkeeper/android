package com.tonapps.tonkeeper.ui.screen.wallet.picker.list.holder

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.emoji.ui.EmojiView
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.extensions.fixW5Title
import com.tonapps.tonkeeper.extensions.getWalletBadges
import com.tonapps.tonkeeper.koin.accountRepository
import com.tonapps.tonkeeper.ui.screen.name.edit.EditNameScreen
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.localization.Localization
import uikit.drawable.CellBackgroundDrawable
import uikit.extensions.drawable

class WalletHolder(
    parent: ViewGroup,
    private val onClick: (WalletEntity) -> Unit
): Holder<Item.Wallet>(parent, R.layout.view_wallet_item) {

    private val backgroundDrawable: CellBackgroundDrawable?
        get() = CellBackgroundDrawable.find(itemView)

    private val colorView = findViewById<View>(R.id.wallet_color)
    private val emojiView = findViewById<EmojiView>(R.id.wallet_emoji)
    private val nameView = findViewById<AppCompatTextView>(R.id.wallet_name)
    private val typesView = findViewById<AppCompatTextView>(R.id.wallet_types)
    private val balanceView = findViewById<AppCompatTextView>(R.id.wallet_balance)
    private val checkView = findViewById<AppCompatImageView>(R.id.check)
    private val editView = findViewById<View>(R.id.edit)
    private val pencilView = findViewById<View>(R.id.pencil)

    override fun onBind(item: Item.Wallet) {
        colorView.backgroundTintList = ColorStateList.valueOf(item.color)
        emojiView.setEmoji(item.emoji, Color.TRANSPARENT)
        nameView.text = item.name.fixW5Title()
        typesView.text = context.getWalletBadges(item.wallet.type, item.wallet.version)

        updatePosition(item)
        updateBalance(item)
        updateSelected(item)
        updateEditMode(item)
        updateFocusAnimation(item)
    }

    fun updateBalance(item: Item.Wallet) {
        val text = if (item.hiddenBalance) {
            HIDDEN_BALANCE
        } else if (item.balance == null) {
            getString(Localization.loading)
        } else {
            item.balance.withCustomSymbol(context)
        }
        balanceView.text = text
    }

    fun updateSelected(item: Item.Wallet) {
        checkView.setImageResource(if (item.selected) UIKitIcon.ic_donemark_otline_28 else 0)
    }

    fun updateEditMode(item: Item.Wallet) {
        if (item.editMode) {
            pencilView.setOnClickListener { navigation?.add(EditNameScreen.newInstance(item.wallet)) }
            itemView.setOnClickListener(null)
            editView.visibility = View.VISIBLE
            checkView.visibility = View.GONE
        } else {
            itemView.setOnClickListener { onClick(item.wallet) }
            pencilView.setOnClickListener(null)
            editView.visibility = View.GONE
            checkView.visibility = View.VISIBLE
        }
    }

    fun updatePosition(item: Item.Wallet) {
        itemView.background = item.position.drawable(context)
    }

    fun updateFocusAnimation(item: Item.Wallet) {
        val drawable = backgroundDrawable ?: return
        if (item.focusAnimation) {
            drawable.start()
        } else {
            drawable.stop()
        }
    }
}