package com.tonapps.tonkeeper.ui.screen.settings.main.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.settings.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentBlueColor
import com.tonapps.uikit.color.stateList
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import uikit.drawable.DotDrawable
import uikit.extensions.drawable
import uikit.extensions.setEndDrawable

class MigrationHolder(
    parent: ViewGroup,
    onClick: ((Item) -> Unit),
) : Holder<Item.Migration>(parent, R.layout.view_settings_migration, onClick) {

    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val subtitleView = findViewById<AppCompatTextView>(R.id.subtitle)
    private val iconView = findViewById<AppCompatImageView>(R.id.icon)

    override fun onBind(item: Item.Migration) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener { onClick(item) }

        titleView.text = getString(Localization.migration)
        titleView.setEndDrawable(
            if (item.showDot) DotDrawable(context, context.accentBlueColor) else null,
        )
        subtitleView.setText(Localization.migration_subtitle)

        iconView.setImageResource(UIKitIcon.ic_download_28)
        iconView.imageTintList = context.accentBlueColor.stateList
    }
}
