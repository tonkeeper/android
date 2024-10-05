package com.tonapps.tonkeeper.ui.screen.init.list

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.api.shortAddress
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.textTertiaryColor
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.wallet.localization.Localization
import uikit.extensions.drawable
import uikit.extensions.reject
import uikit.extensions.setColor
import uikit.widget.CheckBoxView

class Holder(
    parent: ViewGroup,
    private val onClick: (AccountItem, Boolean) -> Boolean
): BaseListHolder<AccountItem>(parent, R.layout.view_select_wallet) {

    private val addressView = findViewById<AppCompatTextView>(R.id.address)
    private val detailsView = findViewById<AppCompatTextView>(R.id.details)
    private val selectedView = findViewById<CheckBoxView>(R.id.selected)

    override fun onBind(item: AccountItem) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener { selectedView.toggle() }

        itemView.isEnabled = !item.ledgerAdded
        addressView.text = item.address.shortAddress
        selectedView.checked = item.selected
        selectedView.isEnabled = !item.ledgerAdded
        selectedView.doOnCheckedChanged = { isChecked ->
            if (!onClick(item, isChecked)) {
                selectedView.checked = !isChecked
                itemView.reject()
            }
        }
        setDetails(item.walletVersion, item.balanceFormat, item.tokens, item.collectibles, item.ledgerIndex != null, item.ledgerAdded)
    }

    private fun setDetails(walletVersion: WalletVersion, balance: CharSequence, tokens: Boolean, collectibles: Boolean, isLedger: Boolean, ledgerAdded: Boolean) {
        val builder = SpannableStringBuilder()
        if (!isLedger) {
            builder.append(walletVersion.title)
            builder.append(DOT)
        }
        builder.append(balance.withCustomSymbol(context))
        if (tokens) {
            builder.append(", ")
            builder.append("tokens")
        }
        if (collectibles) {
            builder.append(", ")
            builder.append("nft")
        }
        if (ledgerAdded) {
            builder.append(DOT)
            builder.append(context.getString(Localization.choose_wallet_already_added))
        }

        if (!isLedger) {
            val spannableString = SpannableString(builder)
            spannableString.setColor(context.textTertiaryColor, walletVersion.title.length, walletVersion.title.length + DOT.length)
            detailsView.text = spannableString
        } else {
            detailsView.text = builder.toString()
        }
    }

    private companion object {
        private const val DOT = " · "
    }
}