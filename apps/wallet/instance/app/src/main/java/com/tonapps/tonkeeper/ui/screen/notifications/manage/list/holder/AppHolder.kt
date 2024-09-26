package com.tonapps.tonkeeper.ui.screen.notifications.manage.list.holder

import android.net.Uri
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.notifications.manage.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.drawable
import uikit.widget.FrescoView
import uikit.widget.SwitchView

class AppHolder(
    parent: ViewGroup,
    private val onToggleCallback: (Uri, Boolean) -> Unit
): Holder<Item.App>(parent, R.layout.view_notifications_app) {

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val pushView = findViewById<SwitchView>(R.id.push)

    init {
        itemView.setOnClickListener {
            pushView.toggle(true)
        }
    }

    override fun onBind(item: Item.App) {
        itemView.background = item.position.drawable(context)
        iconView.setImageURI(item.icon, null)
        titleView.text = item.name
        pushView.setChecked(item.pushEnabled, false)
        pushView.doCheckedChanged = { isChecked, byUser ->
            if (byUser) {
                onToggleCallback(item.uri, isChecked)
            }
        }
    }

}