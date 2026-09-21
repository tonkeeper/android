package com.tonapps.core.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.core.helper.navigationDelegate
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.localization.Localization
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeSmall
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.defaultBundleType
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator

@Composable
fun WalletsSelectorDialog(
    wallets: List<McWalletEntity>,
    selectedWallet: McWalletEntity,
    onSelectWallet: (McWalletEntity) -> Unit,
    onClose: () -> Unit,
    balances: Map<String, CharSequence> = emptyMap(),
    onAddWallet: (() -> Unit)? = null,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)
    val context = LocalContext.current
    val openAddWallet = onAddWallet ?: {
        context.navigationDelegate?.onOpenAddWallet()
    }

    MoonModalDialog(navigator = navigator) {
        MoonTopAppBarSimple(
            title = stringResource(Localization.wallets_list),
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { navigator.close() },
            backgroundColor = Color.Transparent,
        )

        LazyColumn(
            contentPadding = remember { PaddingValues(top = 8.dp, bottom = 16.dp) },
        ) {
            itemsIndexed(
                items = wallets,
                key = { _, w -> w.id },
            ) { index, wallet ->
                WalletRowCell(
                    wallet = wallet,
                    selected = selectedWallet.id == wallet.id,
                    subtitle = balances[wallet.id],
                    position = defaultBundleType(wallets.size, index),
                    onClick = {
                        onSelectWallet(wallet)
                        navigator.close()
                    },
                )
            }

            item(key = "add_wallet") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MoonAccentButton(
                        text = stringResource(Localization.add_wallet),
                        size = ButtonSizeSmall,
                        buttonColors = ButtonColorsSecondary,
                        onClick = {
                            openAddWallet()
                            navigator.close()
                        },
                    )
                }
            }
        }
    }
}
