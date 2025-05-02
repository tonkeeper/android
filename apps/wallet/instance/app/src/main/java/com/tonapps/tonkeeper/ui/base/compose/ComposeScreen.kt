package com.tonapps.tonkeeper.ui.base.compose

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeperx.R
import org.koin.android.ext.android.inject
import uikit.compose.UIKit

abstract class ComposeScreen<C: ScreenContext>(screenContext: C) : BaseWalletScreen<ScreenContext>(R.layout.fragment_compose_host, screenContext) {

    val environment: Environment by inject()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ComposeView>(R.id.compose_view).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                UIKit(
                    theme = environment.theme
                ) {
                    ScreenContent()
                }
            }
        }
    }

    @Composable
    abstract fun ScreenContent()
}