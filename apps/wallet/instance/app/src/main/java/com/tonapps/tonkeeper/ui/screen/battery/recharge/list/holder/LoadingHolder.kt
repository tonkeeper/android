package com.tonapps.tonkeeper.ui.screen.battery.recharge.list.holder

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.ui.screen.battery.recharge.list.Item
import com.tonapps.tonkeeperx.R
import ui.components.moon.screen.MoonLoadingScreen
import ui.theme.MoonTheme

class LoadingHolder(
    parent: ViewGroup,
    private val environment: Environment,
) : Holder<Item.Loading>(parent, R.layout.view_battery_recharge_loading) {

    private val composeView = findViewById<ComposeView>(R.id.compose_view).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    override fun onBind(item: Item.Loading) {
        composeView.setContent {
            MoonTheme(colorScheme = environment.theme) {
                MoonLoadingScreen()
            }
        }
    }
}
