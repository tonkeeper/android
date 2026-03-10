package com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.textAccentColor
import com.tonapps.wallet.localization.Localization

class DescriptionHolder(parent: ViewGroup) :
    Holder<Item.Description>(parent, R.layout.view_staking_description) {

    private val textView = findViewById<AppCompatTextView>(R.id.text)

    override fun onBind(item: Item.Description) {
        textView.text = if (item.isEthena) {
            val aboutText = getString(Localization.about_ethena)
            SpannableStringBuilder("${item.description} $aboutText").apply {
                setSpan(
                    ForegroundColorSpan(context.textAccentColor), length - aboutText.length, length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } else {
            item.description
        }
        if (item.uri == null) {
            textView.setOnClickListener(null)
        } else {
            textView.setOnClickListener {
                BrowserHelper.open(context, item.uri)
            }
        }
    }

}