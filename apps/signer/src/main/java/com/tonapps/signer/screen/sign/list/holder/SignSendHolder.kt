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
        amountView.text = if (item.sendsEntireBalance) {
            getString(R.string.sign_entire_balance)
        } else {
            item.value
        }

        setDangerous(item.dangerous)
        setOptionalText(amount2View, assetText(item))
        setOptionalText(warningView, warningText(item))
        setComment(item.comment)
    }

    private fun assetText(item: SignItem.Send): String? {
        item.tokenAddress?.let { return context.getString(R.string.sign_token_address, it.short4) }
        item.nftAddress?.let { return context.getString(R.string.sign_nft_address, it.short4) }
        return null
    }

    private fun warningText(item: SignItem.Send): String {
        val warnings = mutableListOf<String>()
        item.excessAddress?.let { warnings.add(context.getString(R.string.sign_excess_to, it.short4)) }
        item.unknownOpCode?.let { warnings.add(context.getString(R.string.sign_unknown_operation, it)) }
        if (item.destroysContract) {
            warnings.add(getString(R.string.sign_destroy_warning))
        }
        return warnings.joinToString("\n")
    }

    private fun setComment(comment: String?) {
        if (comment.isNullOrEmpty()) {
            bodyView.visibility = View.GONE
            return
        }

        bodyView.visibility = View.VISIBLE
        commentView.text = comment
    }
}
