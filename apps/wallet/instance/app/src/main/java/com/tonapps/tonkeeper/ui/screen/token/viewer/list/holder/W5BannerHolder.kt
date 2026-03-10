package com.tonapps.tonkeeper.ui.screen.token.viewer.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeper.koin.settingsRepository
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.Item
import com.tonapps.tonkeeper.ui.screen.stories.w5.W5StoriesScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.settings.SettingsRepository
import uikit.navigation.Navigation

class W5BannerHolder(parent: ViewGroup): Holder<Item.W5Banner>(parent, R.layout.view_token_w5_banner) {

    private val settingsRepository: SettingsRepository? by lazy {
        context.settingsRepository
    }

    private val storiesButton = findViewById<View>(R.id.stories)
    private val hideButton = findViewById<View>(R.id.hide)

    override fun onBind(item: Item.W5Banner) {
        hideButton.setOnClickListener {
            settingsRepository?.disableUSDTW5(item.wallet.id)
            itemView.visibility = View.GONE
        }
        storiesButton.setOnClickListener {
            Navigation.from(context)?.add(W5StoriesScreen.newInstance(item.addButton))
        }
    }

}