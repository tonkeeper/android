package com.tonapps.tonkeeper.ui.screen.tronfees.list

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.deposit.screens.qr.QrAssetFragment
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import uikit.extensions.drawable
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.AsyncImageView

class TokenHolder(parent: ViewGroup) : Holder<Item.Token>(parent, R.layout.view_tron_fee_option) {

    private val asyncImageView = itemView.findViewById<AsyncImageView>(R.id.icon)
    private val titleView = itemView.findViewById<AppCompatTextView>(R.id.title)
    private val balanceView = itemView.findViewById<AppCompatTextView>(R.id.balance)
    private val requiredView = itemView.findViewById<AppCompatTextView>(R.id.required)

    override fun onBind(item: Item.Token) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener {
            context.navigation?.add(
                QrAssetFragment.newInstance(item.token, true)
            )
        }
        asyncImageView.setImageURI(item.token.imageUri, this)
        asyncImageView.visibility = View.VISIBLE
        titleView.text = item.token.symbol
        balanceView.text = if (item.transfersCount != null) {
            "${context.getString(Localization.balance_prefix, item.balanceFormat)} (${
                context.resources.getQuantityString(
                    Plurals.transfers,
                    item.transfersCount,
                    item.transfersCount
                )
            })"
        } else {
            context.getString(Localization.balance_prefix, item.balanceFormat)
        }
        requiredView.text = context.getString(Localization.required_prefix, item.amountFormat)
    }
}