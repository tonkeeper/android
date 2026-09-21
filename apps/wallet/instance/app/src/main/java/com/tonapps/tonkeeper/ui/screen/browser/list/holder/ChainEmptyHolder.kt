package com.tonapps.tonkeeper.ui.screen.browser.list.holder

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.wallet.localization.Localization
import ui.components.moon.screen.MoonEmptyScreen
import ui.theme.MoonTheme

class ChainEmptyHolder<I : BaseListItem>(
    parent: ViewGroup,
    private val environment: Environment,
) : BaseListHolder<I>(parent, R.layout.view_browser_chain_empty) {

    private val composeView = findViewById<ComposeView>(R.id.compose_view).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    override fun onBind(item: I) {
        composeView.setContent {
            MoonTheme(colorScheme = environment.theme) {
                MoonEmptyScreen(
                    text = stringResource(Localization.cant_find_anything),
                    minHeight = null,
                )
            }
        }
    }
}
