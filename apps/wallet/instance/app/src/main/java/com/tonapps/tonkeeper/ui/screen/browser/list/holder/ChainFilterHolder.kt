package com.tonapps.tonkeeper.ui.screen.browser.list.holder

import android.view.ViewGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.core.components.ChainFilterBar
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem
import kotlinx.coroutines.flow.StateFlow
import ui.theme.MoonTheme

class ChainFilterHolder<I : BaseListItem>(
    parent: ViewGroup,
    private val selectedChain: StateFlow<Network.Type?>,
    private val onChainSelected: (Network.Type?) -> Unit,
    private val environment: Environment,
    private val contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    private val listHorizontalPadding: Dp = 0.dp,
) : BaseListHolder<I>(parent, R.layout.view_browser_chain_filter) {

    private val composeView = findViewById<ComposeView>(R.id.compose_view).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        setContent {
            MoonTheme(colorScheme = environment.theme) {
                val chain by selectedChain.collectAsState()
                ChainFilterBar(
                    selectedNetwork = chain,
                    onNetworkSelected = onChainSelected,
                    contentPadding = contentPadding,
                )
            }
        }
    }

    init {
        if (listHorizontalPadding.value != 0f) {
            val px = (listHorizontalPadding.value * itemView.resources.displayMetrics.density).toInt()
            (itemView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                params.leftMargin = -px
                params.rightMargin = -px
                itemView.layoutParams = params
            }
        }
    }

    override fun onBind(item: I) = Unit
}
