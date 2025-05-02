package com.tonapps.tonkeeper.ui.screen.settings.main.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.settings.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.localization.Localization
import uikit.extensions.drawable
import uikit.extensions.withDefaultBadge
import uikit.widget.SwitchView

class TronHolder(
    parent: ViewGroup,
    onClick: ((Item) -> Unit)
) : Holder<Item.TronToggle>(parent, R.layout.view_tron_toggle, onClick) {

    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val switchView = findViewById<SwitchView>(R.id.toggle)

    override fun onBind(item: Item.TronToggle) {
        itemView.background = ListCell.Position.SINGLE.drawable(context)

        switchView.setChecked(item.enabled, false)

        switchView.doCheckedChanged = { _, byUser ->
            if (byUser) {
                onClick(item)
            }
        }

        titleView.text = TokenEntity.USDT.symbol.withDefaultBadge(context, Localization.trc20)
    }
}