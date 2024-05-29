package com.tonapps.tonkeeper.ui.screen.backup.main.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.backup.main.list.Item
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import uikit.widget.item.ItemIconView

class RecoveryPhraseHolder(
    parent: ViewGroup,
    private val onClick: (Item) -> Unit
): Holder<Item.RecoveryPhrase>(ItemIconView(parent.context)) {

    private val view = itemView as ItemIconView

    init {
        view.iconRes = UIKitIcon.ic_key_28
        view.text = getString(Localization.recovery_phrase)
    }

    override fun onBind(item: Item.RecoveryPhrase) {
        itemView.setOnClickListener { onClick(item) }
    }
}