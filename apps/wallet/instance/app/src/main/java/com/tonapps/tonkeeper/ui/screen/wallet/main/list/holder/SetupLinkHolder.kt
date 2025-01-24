package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.view.View
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
    private val buttonView = findViewById<View>(R.id.button)
    private val chevronView = findViewById<View>(R.id.chevron)

    override fun onBind(item: Item.SetupLink) {
        itemView.background = item.position.drawable(context)
        iconView.setImageResource(item.iconRes)
        textView.setText(item.textRes)
        itemView.setOnClickListener { click(item) }
        setIconColor(if (item.blue) context.accentBlueColor else context.accentOrangeColor)
        if (item.settingsType == Item.SetupLink.TYPE_TELEGRAM_CHANNEL) {
            buttonView.visibility = View.VISIBLE
            buttonView.setOnClickListener { click(item) }
            chevronView.visibility = View.GONE
        } else {
            buttonView.visibility = View.GONE
            buttonView.setOnClickListener(null)
            chevronView.visibility = View.VISIBLE
        }
    }

    private fun click(item: Item.SetupLink) {
        navigation?.openURL(item.link)
        if (item.settingsType == Item.SetupLink.TYPE_TELEGRAM_CHANNEL) {
            settingsRepository?.setTelegramChannel(item.walletId)
        }
    }

    private fun setIconColor(color: Int) {
        iconView.imageTintList = color.stateList
        iconView.backgroundTintList = color.withAlpha(.12f).stateList
    }

}