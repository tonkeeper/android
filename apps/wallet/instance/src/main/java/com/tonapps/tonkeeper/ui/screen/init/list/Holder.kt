package com.tonapps.tonkeeper.ui.screen.init.list

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.tonkeeper.api.shortAddress
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.textTertiaryColor
import com.tonapps.uikit.list.BaseListHolder
import uikit.extensions.drawable
import uikit.extensions.setColor
import uikit.widget.CheckBoxView

class Holder(
    parent: ViewGroup,
    private val onClick: (AccountItem) -> Unit
): BaseListHolder<AccountItem>(parent, R.layout.view_select_wallet) {

    private val addressView = findViewById<AppCompatTextView>(R.id.address)
    private val detailsView = findViewById<AppCompatTextView>(R.id.details)
    private val selectedView = findViewById<CheckBoxView>(R.id.selected)

    override fun onBind(item: AccountItem) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener { onClick(item) }
        addressView.text = item.address.shortAddress
        selectedView.checked = item.selected
        setDetails(item.walletVersion, item.balanceFormat, item.tokens, item.collectibles)
    }

    private fun setDetails(walletVersion: WalletVersion, balance: CharSequence, tokens: Boolean, collectibles: Boolean) {
        val builder = StringBuilder()
        builder.append(walletVersion.title)
        builder.append(DOT)
        builder.append(balance)
        if (tokens) {
            builder.append(", ")
            builder.append("tokens")
        }
        if (collectibles) {
            builder.append(", ")
            builder.append("nft")
        }

        val spannableString = SpannableString(builder)
        spannableString.setColor(context.textTertiaryColor, walletVersion.title.length, walletVersion.title.length + DOT.length)
        detailsView.text = spannableString
    }

    private companion object {
        private const val DOT = " · "
    }
}