package com.tonkeeper.core.history.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatTextView
import com.facebook.drawee.view.SimpleDraweeView
import com.tonkeeper.R
import com.tonkeeper.core.history.list.item.HistoryItem
import com.tonkeeper.dialog.TransactionDialog
import uikit.list.ListCell.Companion.drawable
import uikit.widget.LoaderView

class HistoryActionHolder(parent: ViewGroup): HistoryHolder<HistoryItem.Action>(parent, R.layout.view_history_action) {

    private val amountColorReceived = context.getColor(uikit.R.color.accentGreen)
    private val amountColorDefault = context.getColor(uikit.R.color.textPrimary)

    private val loaderView = findViewById<LoaderView>(R.id.loader)
    private val iconView = findViewById<SimpleDraweeView>(R.id.icon)
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

    override fun onBind(item: HistoryItem.Action) {
        itemView.setOnClickListener { TransactionDialog.open(context, item) }
        itemView.background = item.position.drawable(context)
        titleView.setText(getTitle(item.action))
        subtitleView.text = item.subtitle
        dateView.text = item.date

        if (item.iconURL.isNullOrEmpty()) {
            iconView.setImageResource(getIcon(item.action))
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

    private fun bindAmount(item: HistoryItem.Action) {
        when (item.action) {
            HistoryItem.Action.Type.NftSend, HistoryItem.Action.Type.NftReceived -> {
                amountView.setTextColor(amountColorDefault)
                amountView.text = item.tokenCode
            }
            HistoryItem.Action.Type.Received -> {
                amountView.setTextColor(amountColorReceived)
                amountView.text = "+ %s %s".format(item.value, item.tokenCode).trim()
            }
            HistoryItem.Action.Type.Send -> {
                amountView.setTextColor(amountColorDefault)
                amountView.text = "- %s %s".format(item.value, item.tokenCode).trim()
            }
            else -> {
                amountView.setTextColor(getAmountColor(item.value))
                amountView.text = "+ %s %s".format(item.value, item.tokenCode).trim()
            }
        }

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

    private fun bindNft(item: HistoryItem.Action) {
        if (!item.hasNft) {
            nftView.visibility = View.GONE
            return
        }
        nftView.visibility = View.VISIBLE
        nftIconView.setImageURI(item.nftImageURL)
        nftNameView.text = item.nftTitle
        nftCollectionView.text = item.nftCollection
    }

    private fun getTitle(action: HistoryItem.Action.Type): Int {
        return when (action) {
            HistoryItem.Action.Type.Received, HistoryItem.Action.Type.NftReceived -> R.string.receive
            HistoryItem.Action.Type.Send, HistoryItem.Action.Type.NftSend -> R.string.send
            HistoryItem.Action.Type.CallContract -> R.string.call_contract
            HistoryItem.Action.Type.Swap -> R.string.swap
        }
    }

    private fun getIcon(action: HistoryItem.Action.Type): Int {
        return when (action) {
            HistoryItem.Action.Type.Received, HistoryItem.Action.Type.NftReceived -> R.drawable.ic_tray_arrow_down_28
            HistoryItem.Action.Type.Send, HistoryItem.Action.Type.NftSend -> R.drawable.ic_tray_arrow_up_28
            HistoryItem.Action.Type.CallContract -> R.drawable.ic_gear_28
            HistoryItem.Action.Type.Swap -> R.drawable.ic_swap_horizontal_alternative_28
        }
    }

    @ColorInt
    private fun getAmountColor(amount: String): Int {
        return if (amount.startsWith("-")) {
            amountColorDefault
        } else {
            amountColorReceived
        }
    }
}