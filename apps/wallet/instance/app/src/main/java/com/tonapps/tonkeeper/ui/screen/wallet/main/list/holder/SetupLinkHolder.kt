package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.koin.settingsRepository
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentBlueColor
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.stateList
import uikit.extensions.drawable
import uikit.extensions.withAlpha
import uikit.navigation.Navigation

class SetupLinkHolder(parent: ViewGroup): Holder<Item.SetupLink>(parent, R.layout.view_wallet_setup_link) {

    private val settingsRepository = context.settingsRepository

    private val iconView = findViewById<AppCompatImageView>(R.id.icon)
    private val textView = findViewById<AppCompatTextView>(R.id.text)

    override fun onBind(item: Item.SetupLink) {
        itemView.background = item.position.drawable(context)
        iconView.setImageResource(item.iconRes)
        textView.setText(item.textRes)
        itemView.setOnClickListener {
            Navigation.from(context)?.openURL(item.link, item.external)
            if (item.external) {
                settingsRepository?.telegramChannel = false
            }
        }
        setIconColor(if (item.blue) context.accentBlueColor else context.accentOrangeColor)
    }

    private fun setIconColor(color: Int) {
        iconView.imageTintList = color.stateList
        iconView.backgroundTintList = color.withAlpha(.12f).stateList
    }

}