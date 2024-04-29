package com.tonapps.signer.screen.sign.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.signer.R
import com.tonapps.signer.extensions.short4
import com.tonapps.signer.screen.sign.list.SignItem
import com.tonapps.uikit.icon.UIKitIcon
import uikit.extensions.drawable

class SignSendHolder(parent: ViewGroup): SignHolder<SignItem.Send>(parent) {

    init {
        iconView.setImageResource(UIKitIcon.ic_tray_arrow_up_28)
        titleView.setText(R.string.send)
    }

    override fun onBind(item: SignItem.Send) {
        itemView.background = item.position.drawable(context)
        subtitleView.text = item.target.short4
        amountView.text = item.value

        setComment(item.comment)
        setAmount2(item.value2)
    }

    private fun setComment(comment: String?) {
        if (comment.isNullOrEmpty()) {
            bodyView.visibility = View.GONE
            return
        }

        bodyView.visibility = View.VISIBLE
        commentView.text = comment
    }

    private fun setAmount2(amount2: String?) {
        if (amount2.isNullOrEmpty()) {
            amount2View.visibility = View.GONE
            return
        }

        amount2View.visibility = View.VISIBLE
        amount2View.text = amount2.short4
    }

}