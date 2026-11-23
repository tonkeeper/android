package com.tonapps.wallet.features.ledger.wallet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.core.components.painterResource
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.wallet.data.ledger.connection.LedgerActivity
import com.tonapps.wallet.data.ledger.connection.LedgerActivityOutcome
import com.tonapps.wallet.data.ledger.connection.LedgerConnectionStatus
import com.tonapps.wallet.features.ledger.common.LedgerDeviceHeader
import com.tonapps.wallet.features.ledger.common.LedgerMessage
import com.tonapps.wallet.features.ledger.common.displayText
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonChevronRight
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLoader
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonEmptyCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.TextCell
import ui.components.moon.container.MoonScaffold
import ui.preview.ThemedPreview
import ui.shortAddress
import ui.theme.UIKit

@Composable
internal fun LedgerWalletScreen(
    feature: LedgerWalletFeature,
    onClose: () -> Unit,
    onPairAgain: () -> Unit,
) {
    val state by feature.state.collectAsStateWithLifecycle()
    val connection by feature.connection.collectAsStateWithLifecycle()
    val activity by feature.activity.collectAsStateWithLifecycle()
    LedgerWalletScreenInternal(
        state = state,
        connection = connection,
        activity = activity,
        actions = feature,
        onClose = onClose,
        onPairAgain = onPairAgain,
    )
}

@Composable
private fun LedgerWalletScreenInternal(
    state: LedgerWalletState,
    connection: LedgerConnectionStatus?,
    activity: List<LedgerActivity>,
    actions: LedgerWalletActions,
    onClose: () -> Unit,
    onPairAgain: () -> Unit,
) {
    MoonScaffold(
        modifier = Modifier.fillMaxSize(),
        title = stringResource(Localization.ledger_mc_wallet_title),
        onClose = onClose,
    ) {
        val device = state.device
        if (device == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.isLoadFailed) {
                    MoonEmptyCell(
                        title = stringResource(Localization.unknown_error),
                        firstButtonText = stringResource(Localization.try_again),
                        onFirstClick = actions::retryLoad,
                    )
                } else if (!state.isLoaded) {
                    MoonLoader(modifier = Modifier.size(24.dp))
                }
            }
            return@MoonScaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            item {
                DeviceHeader(device, displayText = connection.displayText)
                SectionTitle(stringResource(Localization.ledger_mc_connection_title))
                LedgerConnectionCell(
                    status = connection,
                    onCheck = actions::checkConnection,
                    onCancel = actions::cancelCheck,
                )
                Spacer(modifier = Modifier.height(16.dp))
                SectionTitle(stringResource(Localization.ledger_mc_wallet_networks))
            }
            itemsIndexed(state.networks, key = { _, row -> row.id }) { index, row ->
                MoonBundleCell(position = MoonBundlePosition.default(state.networks.size, index)) {
                    NetworkCell(
                        row = row,
                        isBusy = state.busyChain == row.chain,
                        onVerify = { actions.verify(row.chain) },
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
                WalletActions(
                    missingNetworks = state.missingNetworks,
                    isAddingNetworks = state.isAddingNetworks,
                    onAddNetworks = actions::addNetworks,
                    onPairAgain = onPairAgain,
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
            if (activity.isNotEmpty()) {
                item {
                    SectionTitle(stringResource(Localization.ledger_mc_activity_title))
                }
                itemsIndexed(activity, key = { _, entry -> "activity_${entry.id}" }) { index, entry ->
                    LedgerActivityCell(entry, MoonBundlePosition.default(activity.size, index))
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun DeviceHeader(device: LedgerDeviceInfo, displayText: String) {
    Spacer(modifier = Modifier.height(16.dp))
    LedgerDeviceHeader(displayText = displayText)
    Spacer(modifier = Modifier.height(16.dp))
    LedgerMessage(
        title = device.deviceName,
        subtitle = stringResource(Localization.ledger_mc_wallet_account, device.accountNumber),
    )
    if (device.modelName != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MoonLabel(text = device.modelName)
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun WalletActions(
    missingNetworks: String?,
    isAddingNetworks: Boolean,
    onAddNetworks: () -> Unit,
    onPairAgain: () -> Unit,
) {
    if (missingNetworks != null) {
        MoonBundleCell(position = MoonBundlePosition.default(1, 0)) {
            TextCell(
                title = stringResource(Localization.ledger_mc_add_networks),
                subtitle = missingNetworks,
                titleColor = UIKit.colorScheme.accent.blue,
                maxLinesSubtitle = 2,
                content = {
                    if (isAddingNetworks) {
                        MoonLoader(modifier = Modifier.size(20.dp))
                    }
                },
                onClick = onAddNetworks,
            )
        }
        MoonItemSubtitle(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 16.dp),
            text = stringResource(Localization.ledger_mc_add_networks_hint),
            maxLines = Int.MAX_VALUE,
        )
    }
    MoonBundleCell(position = MoonBundlePosition.default(1, 0)) {
        TextCell(
            title = stringResource(Localization.ledger_mc_pair_again),
            subtitle = stringResource(Localization.ledger_mc_pair_again_hint),
            maxLinesSubtitle = 3,
            content = { MoonChevronRight() },
            onClick = onPairAgain,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        text = text,
        style = UIKit.typography.label1,
        color = UIKit.colorScheme.text.primary,
    )
}

@Composable
private fun NetworkCell(row: LedgerNetworkRow, isBusy: Boolean, onVerify: () -> Unit) {
    TextCell(
        title = row.chain.name,
        subtitle = row.displayAddress.shortAddress,
        image = { MoonItemImage(painter = row.chain.painterResource(), size = 28.dp) },
        content = {
            if (isBusy) {
                MoonLoader(modifier = Modifier.size(20.dp))
            } else {
                Text(
                    text = stringResource(Localization.ledger_mc_verify_on_ledger),
                    style = UIKit.typography.label2,
                    color = UIKit.colorScheme.accent.blue,
                )
            }
        },
        onClick = onVerify,
    )
}

@Preview
@Composable
private fun LedgerWalletScreenPreview() {
    ThemedPreview {
        LedgerWalletScreenInternal(
            state = LedgerWalletState(
                device = LedgerDeviceInfo(deviceName = "Stax 1A2B", modelName = "Ledger Stax", accountNumber = 1),
                networks = listOf(
                    LedgerNetworkRow("ton", Chain.Ton.Mainnet, "UQAO_r9SsH_DdHiiTJrz1Skuh7Jo_Ji3l9faCk5wf1kdSg7W"),
                    LedgerNetworkRow("eth", Chain.Ethereum.Mainnet, "0xfaCF3b5E1d2A0B7C9d8E6F4a3B2c1D0e9F8a7Cba2"),
                ),
                missingNetworks = "Bitcoin",
                isLoaded = true,
            ),
            connection = LedgerConnectionStatus.Nearby,
            activity = listOf(
                LedgerActivity(1, "AA:BB", LedgerPurpose.SignTransaction, 0L, LedgerActivityOutcome.Done),
                LedgerActivity(0, "AA:BB", LedgerPurpose.VerifyAddress, 0L, LedgerActivityOutcome.InProgress(LedgerProgress.WaitingUnlock)),
            ),
            actions = object : LedgerWalletActions {},
            onClose = {},
            onPairAgain = {},
        )
    }
}
