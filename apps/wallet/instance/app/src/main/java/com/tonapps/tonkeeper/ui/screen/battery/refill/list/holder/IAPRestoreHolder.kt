package com.tonapps.tonkeeper.ui.screen.battery.refill.list.holder

import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.battery.refill.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.localization.Localization
import uikit.extensions.setColor

class IAPRestoreHolder(
    parent: ViewGroup,
    private val onRestorePurchases: () -> Unit,
) : Holder<Item.RestoreIAP>(parent, R.layout.view_battery_restore_iap) {

    private val textView = itemView.findViewById<AppCompatTextView>(R.id.text)

    override fun onBind(item: Item.RestoreIAP) {
        val disclaimerText = if (item.chargeEnabled) {
            getString(Localization.battery_disclaimer)
        } else {
            getString(Localization.charging_unavailable)
        }
        val restoreText = getString(Localization.restore_purchases)
        val textDivider = if (item.chargeEnabled) " " else "\n"
        val span = SpannableString("$disclaimerText$textDivider$restoreText")

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                onRestorePurchases()
            }
            override fun updateDrawState(ds: android.text.TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false
            }
        }

        span.setSpan(
            clickableSpan,
            disclaimerText.length + 1,
            span.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        span.setColor(context.textSecondaryColor, disclaimerText.length + 1, span.length)

        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.text = span
    }
}