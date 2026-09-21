package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.ui.screen.collectibles.main.CollectiblesScreen
import com.tonapps.tonkeeper.ui.screen.nft.NftScreen
import com.tonapps.portfolio.screens.wallet.WalletCollectiblesState
import com.tonapps.portfolio.screens.wallet.components.CollectiblesSection
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeperx.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ui.theme.MoonTheme

class CollectiblesHolder(parent: ViewGroup): Holder<Item.Collectibles>(parent, R.layout.view_wallet_collectibles), KoinComponent {

    private val environment: Environment by inject()

    private val composeView = findViewById<ComposeView>(R.id.compose_view).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    override fun onBind(item: Item.Collectibles) {
        composeView.setContent {
            MoonTheme(colorScheme = environment.theme) {
                CollectiblesSection(
                    state = WalletCollectiblesState(
                        items = item.nfts,
                        allHidden = item.allHidden,
                    ),
                    onHeaderClick = {
                        navigation?.add(CollectiblesScreen.newInstance(from = "wallet"))
                    },
                    onSeeAllClick = {
                        navigation?.add(CollectiblesScreen.newInstance(from = "wallet"))
                    },
                    onNftClick = { nft ->
                        navigation?.add(NftScreen.newInstance(nft))
                    },
                )
            }
        }
    }
}
