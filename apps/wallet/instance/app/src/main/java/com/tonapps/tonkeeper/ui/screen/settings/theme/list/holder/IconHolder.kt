package com.tonapps.tonkeeper.ui.screen.settings.theme.list.holder

import android.app.Activity
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.core.LauncherIcon
import com.tonapps.tonkeeper.ui.screen.settings.theme.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import uikit.extensions.activity

class IconHolder(parent: ViewGroup): Holder<Item.Icon>(parent, R.layout.view_theme_icon) {

    private val activity: Activity?
        get() = itemView.context.activity

    private val iconView = findViewById<AppCompatImageView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)

    override fun onBind(item: Item.Icon) {
        iconView.setImageResource(item.icon.iconRes)
        titleView.text = item.icon.type
        itemView.setOnClickListener {
            LauncherIcon.setEnable(itemView.context, item.icon)
            Toast.makeText(itemView.context, getString(Localization.app_icon_changed), Toast.LENGTH_SHORT).show()
            activity?.finish()
        }
    }

}