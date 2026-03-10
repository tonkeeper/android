package com.tonapps.tonkeeper.ui.base.compose

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeperx.R
import org.koin.android.ext.android.inject
import ui.theme.LunaTheme

abstract class ComposeScreen<C: ScreenContext>(screenContext: C) : BaseWalletScreen<ScreenContext>(R.layout.fragment_compose_host, screenContext) {

    val environment: Environment by inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val theme = environment.theme
        view.findViewById<ComposeView>(R.id.compose_view).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LunaTheme(colorScheme = theme) {
                    ScreenContent()
                }
            }
        }
    }

    @Composable
    @NonRestartableComposable
    abstract fun ScreenContent()
}