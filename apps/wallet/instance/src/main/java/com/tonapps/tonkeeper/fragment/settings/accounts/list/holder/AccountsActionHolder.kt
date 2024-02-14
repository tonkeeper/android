package com.tonapps.tonkeeper.fragment.settings.accounts.list.holder

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.uikit.color.UIKitColor
import com.tonapps.tonkeeper.dialog.IntroWalletDialog
import com.tonapps.tonkeeper.fragment.settings.accounts.list.item.AccountsActionItem
import uikit.extensions.drawable
import uikit.widget.item.ItemIconView

class AccountsActionHolder(
    parent: ViewGroup
): AccountsHolder<AccountsActionItem>(ItemIconView(parent.context)) {

    private val view = itemView as ItemIconView

    init {
        view.layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onBind(item: AccountsActionItem) {
        view.background = item.position.drawable(context)
        view.setIconTintColor(context.getColor(UIKitColor.accentBlue))
        view.iconRes = item.iconRes
        view.text = getString(item.titleRes)
        view.setOnClickListener { click(item) }
    }

    private fun click(item: AccountsActionItem) {
        when (item.id) {
            AccountsActionItem.NEW_WALLET_ID -> {
                IntroWalletDialog(context).show()
            }
        }
    }
}