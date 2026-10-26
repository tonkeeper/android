package com.tonapps.wallet.features.events.screens.market

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.screens.details.PerpsMarketHistoryRequest
import com.tonapps.perps.screens.details.PerpsMarketHistorySection
import com.tonapps.wallet.features.events.components.ActivityEventCell
import com.tonapps.wallet.features.events.screens.WalletActivityListItem
import com.tonapps.wallet.features.events.screens.details.EventDetailsModal
import com.tonapps.wallet.localization.Localization
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.MoonBundleTitleCell
import ui.theme.UIKit

internal class PerpsMarketHistorySectionImpl : PerpsMarketHistorySection {

    @Composable
    override fun Content(
        request: PerpsMarketHistoryRequest,
        market: PerpMarket,
        onSeeAll: () -> Unit,
    ) {
        val feature = koinViewModel<PerpsMarketHistoryFeature>(
            key = request.walletId,
            parameters = { parametersOf(request.walletId, request.marketIndex) },
        )
        LaunchedEffect(feature, request.attempt) {
            feature.sendAction(PerpsMarketHistoryAction.Reload(request.attempt))
        }
        val items by feature.items.collectAsState()
        if (items.isEmpty()) {
            return
        }
        var selected by remember { mutableStateOf<WalletActivityListItem.Entry?>(null) }
        Column(modifier = Modifier.fillMaxWidth()) {
            MoonBundleTitleCell(
                title = stringResource(Localization.transaction_history),
                content = {
                    Text(
                        text = stringResource(Localization.see_all),
                        modifier = Modifier.clickable(onClick = onSeeAll),
                        style = UIKit.typography.body2,
                        color = UIKit.colorScheme.text.accent,
                    )
                },
            )
            items.forEachIndexed { index, entry ->
                ActivityEventCell(
                    activity = entry.activity,
                    hiddenBalance = entry.hiddenBalance,
                    position = MoonBundlePosition.default(size = items.size, index = index),
                    onClick = { selected = entry },
                    showDate = true,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        selected?.let { entry ->
            EventDetailsModal(
                event = entry.activity,
                wallet = null,
                hiddenBalance = entry.hiddenBalance,
                perpsMarket = market,
                onDismiss = { selected = null },
            )
        }
    }
}
