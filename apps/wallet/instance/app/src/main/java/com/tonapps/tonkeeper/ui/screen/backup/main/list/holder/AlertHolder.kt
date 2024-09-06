package com.tonapps.tonkeeper.ui.screen.backup.main.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.backup.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.accentRedColor
import com.tonapps.uikit.color.constantBlackColor
import com.tonapps.uikit.color.stateList
import com.tonapps.uikit.color.textPrimaryColor
import com.tonapps.wallet.localization.Localization
import uikit.extensions.withAlpha

class AlertHolder(parent: ViewGroup): Holder<Item.Alert>(parent, R.layout.view_backup_alert) {

    private val alertView = findViewById<AppCompatTextView>(R.id.alert)

    override fun onBind(item: Item.Alert) {
        alertView.text = context.getString(Localization.backup_alert_message, item.balanceFormat)
        if (item.red) {
            alertView.backgroundTintList = context.accentRedColor.stateList
            alertView.setTextColor(context.textPrimaryColor.withAlpha(.76f))
        } else {
            alertView.backgroundTintList = context.accentOrangeColor.stateList
            alertView.setTextColor(context.constantBlackColor.withAlpha(.76f))
        }
    }

}