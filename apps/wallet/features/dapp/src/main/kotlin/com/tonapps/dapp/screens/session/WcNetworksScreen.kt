package com.tonapps.dapp.screens.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tonapps.core.components.VerticalAssetCell
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.localization.Localization
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonBottomBar
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.container.MoonScaffold
import ui.components.moon.moonBottomBarHeight

@Composable
fun WcNetworksScreen(
    accounts: List<AccountEntity>,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    MoonScaffold(
        modifier = Modifier.fillMaxSize(),
        title = stringResource(Localization.networks_to_connect),
        onClose = onClose,
    ) {
        val bottomOverlayHeight = moonBottomBarHeight()

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomOverlayHeight),
            ) {
                itemsIndexed(
                    items = accounts,
                    key = { _, account -> account.id },
                ) { index, account ->
                    MoonBundleCell(
                        position = MoonBundlePosition.default(accounts.size, index),
                    ) {
                        VerticalAssetCell(account)
                    }
                }
            }

            MoonBottomBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                MoonAccentButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(Localization.ok),
                    size = ButtonSizeLarge,
                    buttonColors = ButtonColorsSecondary,
                    onClick = onClose,
                )
            }
        }
    }
}
