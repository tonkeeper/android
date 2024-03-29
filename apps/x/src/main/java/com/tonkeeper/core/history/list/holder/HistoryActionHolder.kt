package com.tonkeeper.core.history.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatTextView
import com.facebook.drawee.view.SimpleDraweeView
import com.tonapps.tonkeeperx.R
import com.tonkeeper.core.history.ActionType
import com.tonkeeper.core.history.HistoryHelper
import com.tonkeeper.core.history.iconRes
import com.tonkeeper.core.history.list.item.HistoryItem
import com.tonkeeper.core.history.nameRes
import com.tonkeeper.dialog.TransactionDialog
import com.tonkeeper.fragment.nft.NftScreen
import uikit.list.ListCell.Companion.drawable
import uikit.widget.FrescoView
import uikit.widget.LoaderView

class HistoryActionHolder(
    parent: ViewGroup,
    private val disableOpenAction: Boolean
): HistoryHolder<HistoryItem.Event>(parent, R.layout.view_history_action) {

    private val amountColorReceived = context.getColor(uikit.R.color.accentGreen)
    private val amountColorDefault = context.getColor(uikit.R.color.textPrimary)
    private val amountColorTertiary = context.getColor(uikit.R.color.textTertiary)

    private val loaderView = findViewById<LoaderView>(R.id.loader)
    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val subtitleView = findViewById<AppCompatTextView>(R.id.subtitle)
    private val commentView = findViewById<AppCompatTextView>(R.id.comment)
    private val amountView = findViewById<AppCompatTextView>(R.id.amount)
    private val amount2View = findViewById<AppCompatTextView>(R.id.amount2)
    private val dateView = findViewById<AppCompatTextView>(R.id.date)

    private val nftView = findViewById<View>(R.id.nft)
    private val nftIconView = findViewById<SimpleDraweeView>(R.id.nft_icon)
    private val nftNameView = findViewById<AppCompatTextView>(R.id.nft_name)
    private val nftCollectionView = findViewById<AppCompatTextView>(R.id.nft_collection)

    override fun onBind(item: HistoryItem.Event) {
        if (!disableOpenAction) {
            itemView.setOnClickListener { TransactionDialog.open(context, item) }
        }

        itemView.background = item.position.drawable(context)
        titleView.setText(item.action.nameRes)
        subtitleView.text = item.subtitle
        dateView.text = item.date

        if (item.iconURL.isNullOrEmpty()) {
            iconView.setImageResource(item.action.iconRes)
        } else {
            iconView.setImageURI(item.iconURL)
        }

        bindPending(item.pending)
        bindComment(item.comment)
        bindNft(item)
        bindAmount(item)
    }

    private fun bindPending(pending: Boolean) {
        loaderView.visibility = if (pending) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun bindAmount(item: HistoryItem.Event) {
        if (item.action == ActionType.WithdrawStakeRequest) {
            amountView.setTextColor(amountColorTertiary)
        } else {
            amountView.setTextColor(getAmountColor(item.value))
        }
        amountView.text = item.value

        if (item.value2.isEmpty()) {
            amount2View.visibility = View.GONE
        } else {
            amount2View.visibility = View.VISIBLE
            amount2View.text = item.value2
        }
    }

    private fun bindComment(comment: String?) {
        if (comment.isNullOrBlank()) {
            commentView.visibility = View.GONE
        } else {
            commentView.visibility = View.VISIBLE
            commentView.text = comment
        }
    }

    private fun bindNft(item: HistoryItem.Event) {
        if (!item.hasNft) {
            nftView.visibility = View.GONE
            return
        }

        nftView.visibility = View.VISIBLE
        nftView.setOnClickListener {
            nav?.add(NftScreen.newInstance(item.nftAddress!!))
        }
        nftIconView.setImageURI(item.nftImageURL)
        nftNameView.text = item.nftTitle
        nftCollectionView.text = item.nftCollection
    }

    @ColorInt
    private fun getAmountColor(amount: String): Int {
        if (amount == HistoryHelper.MINUS_SYMBOL) {
            return amountColorTertiary
        }
        return if (amount.startsWith(HistoryHelper.PLUS_SYMBOL)) {
            amountColorReceived
        } else {
            amountColorDefault
        }
    }
}