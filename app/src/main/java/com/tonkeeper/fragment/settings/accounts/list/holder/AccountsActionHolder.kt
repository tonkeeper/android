package com.tonkeeper.fragment.settings.accounts.list.holder

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.R
import com.tonkeeper.dialog.IntroWalletDialog
import com.tonkeeper.fragment.settings.accounts.list.item.AccountsActionItem
import uikit.drawable.CellBackgroundDrawable
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
        view.background = CellBackgroundDrawable(context, item.position)
        view.setIconTintColor(context.getColor(uikit.R.color.accentBlue))
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