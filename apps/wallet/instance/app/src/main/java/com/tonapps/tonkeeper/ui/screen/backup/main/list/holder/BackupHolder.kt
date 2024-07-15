package com.tonapps.tonkeeper.ui.screen.backup.main.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.backup.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import uikit.extensions.drawable

class BackupHolder(
    parent: ViewGroup,
    private val onClick: (Item) -> Unit
): Holder<Item.Backup>(parent, R.layout.view_backup) {

    private val dateView = findViewById<AppCompatTextView>(R.id.date)

    override fun onBind(item: Item.Backup) {
        itemView.setOnClickListener { onClick(item) }
        itemView.background = item.position.drawable(context)
        dateView.text = context.getString(Localization.manual_backup_date, item.date)
    }

}