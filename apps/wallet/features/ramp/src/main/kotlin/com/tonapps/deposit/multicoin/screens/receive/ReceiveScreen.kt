package com.tonapps.deposit.multicoin.screens.receive

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.deposit.common.QrDialog
import com.tonapps.deposit.multicoin.components.QrAccountCell
import com.tonapps.wallet.localization.Localization
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.container.MoonScaffold
import ui.components.moon.dialog.MoonDialogDisposableScope
import ui.workaround.hideKeyboardOnScrollConnection

@Composable
fun ReceiveScreen(
    feature: ReceiveFeature,
    onClose: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val accounts by feature.stateAccounts.collectAsState()
    val selectedAccount by feature.selectedAccount.collectAsState()

    MoonScaffold(
        title = stringResource(Localization.receiving_address),
        subtitle = stringResource(Localization.receiving_address_subtitle),
        onClose = onClose,
        onBack = onBack,
    ) {
        LazyColumn(
            modifier = Modifier.nestedScroll(hideKeyboardOnScrollConnection()),
            contentPadding = PaddingValues(
                top = 6.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
            )
        ) {
            itemsIndexed(
                items = accounts,
                key = { _, account -> account.id },
                contentType = { _, account -> account::class }
            ) { index, account ->
                QrAccountCell(
                    account = account,
                    position = MoonBundlePosition.default(accounts.size, index),
                    onOpenQr = { feature.selectAccount(account) }
                )
            }
        }
    }

    selectedAccount?.let {
        MoonDialogDisposableScope {
            QrDialog(account = it, onClose = { feature.selectAccount(null) })
        }
    }
}
