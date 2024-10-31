package com.tonapps.tonkeeper.ui.screen.settings.apps.list.holder

import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import com.facebook.imagepipeline.common.ResizeOptions
import com.tonapps.tonkeeper.ui.screen.settings.apps.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.dapps.entities.AppEntity
import uikit.extensions.drawable
import uikit.widget.FrescoView

class AppHolder(
    parent: ViewGroup,
    private val disconnectApp: (app: AppEntity) -> Unit
): Holder<Item.App>(parent, R.layout.view_settings_app) {

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val hostView = findViewById<AppCompatTextView>(R.id.host)
    private val disconnectButton = findViewById<Button>(R.id.disconnect)

    override fun onBind(item: Item.App) {
        itemView.background = item.position.drawable(context)
        iconView.setImageURI(item.iconUrl, ResizeOptions.forSquareSize(44))
        titleView.text = item.title
        hostView.text = item.host
        disconnectButton.setOnClickListener {
            disconnectApp(item.app)
        }
    }
}