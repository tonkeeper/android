package com.tonapps.wallet.features.ledger.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerAppSetting
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import com.tonapps.ledgerkit.LedgerConfirmDetail
import com.tonapps.ledgerkit.LedgerConfirmKind
import java.math.BigDecimal
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerProgress
import com.tonapps.ledgerkit.controller.LedgerPurpose
import com.tonapps.ledgerkit.controller.LedgerRequest
import com.tonapps.ledgerkit.controller.LedgerOperationState
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.features.ledger.common.LedgerAddressBox
import com.tonapps.wallet.features.ledger.common.LedgerSystemAction
import com.tonapps.wallet.features.ledger.common.displayText
import com.tonapps.wallet.features.ledger.common.systemAction
import com.tonapps.wallet.localization.Localization
import ui.components.moon.ButtonColorsPrimary
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonTopAppBarSimple
import ui.preview.ThemedPreview
import ui.theme.UIKit

@Composable
internal fun LedgerProgressSheet(
    view: LedgerProgressView,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onSystemAction: (LedgerSystemAction) -> Unit,
    onPairAgain: (String) -> Unit = {},
) {
    val state = view.state
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(UIKit.colorScheme.background.page)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MoonTopAppBarSimple(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = onClose,
            backgroundColor = Color.Transparent,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LedgerStepsCard(
            connecting = view.stage == LedgerStage.Connect,
            isUsb = view.isUsb,
            displayText = state.displayText,
            steps = ledgerSteps(view),
        )
        if (state is LedgerOperationState.Active) {
            ActiveDetails(state.progress)
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (state is LedgerOperationState.Failed) {
            FailedActions(state, onRetry, onSystemAction, onPairAgain)
        }
        if (state !is LedgerOperationState.Done) {
            MoonAccentButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                text = stringResource(if (state is LedgerOperationState.Failed) { Localization.close } else { Localization.cancel }),
                buttonColors = ButtonColorsSecondary,
                size = ButtonSizeLarge,
                onClick = onClose,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ActiveDetails(progress: LedgerProgress) {
    if (progress is LedgerProgress.VerifyAddress) {
        Spacer(modifier = Modifier.height(16.dp))
        LedgerAddressBox(address = progress.address)
    }
    val detail = (progress as? LedgerProgress.Confirm)?.detail
    if (detail != null) {
        Spacer(modifier = Modifier.height(16.dp))
        ConfirmDetail(detail)
    }
}

@Composable
private fun ConfirmDetail(detail: LedgerConfirmDetail) {
    val (hint, value) = when (detail) {
        is LedgerConfirmDetail.Hash -> stringResource(Localization.ledger_mc_progress_hash_hint) to detail.hex.uppercase().chunked(HASH_GROUP).joinToString(" ")
        is LedgerConfirmDetail.MessageHash -> stringResource(Localization.ledger_mc_progress_message_hash_hint) to detail.base58.chunked(HASH_GROUP).joinToString(" ")
        is LedgerConfirmDetail.BitcoinChange -> stringResource(Localization.ledger_mc_progress_bitcoin_change, bitcoinAmount(detail.amountSats)) to detail.address
    }
    Text(
        text = hint,
        style = UIKit.typography.body2,
        color = UIKit.colorScheme.text.secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
    )
    Spacer(modifier = Modifier.height(8.dp))
    LedgerAddressBox(address = value)
}

private fun bitcoinAmount(sats: Long): String =
    BigDecimal.valueOf(sats).movePointLeft(BITCOIN_DECIMALS).stripTrailingZeros().toPlainString() + " BTC"

private const val HASH_GROUP = 8
private const val BITCOIN_DECIMALS = 8

private val LedgerFailure.canPairAgain: Boolean
    get() = this == LedgerFailure.DeviceNotFound ||
        this == LedgerFailure.PairingRefused ||
        this == LedgerFailure.PairingLost ||
        this is LedgerFailure.AddressMismatch

@Composable
private fun FailedActions(
    state: LedgerOperationState.Failed,
    onRetry: () -> Unit,
    onSystemAction: (LedgerSystemAction) -> Unit,
    onPairAgain: (String) -> Unit,
) {
    val action = state.failure.systemAction
    if (action != null) {
        MoonAccentButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            text = stringResource(action.label),
            buttonColors = if (state.canRetry) { ButtonColorsSecondary } else { ButtonColorsPrimary },
            size = ButtonSizeLarge,
            onClick = { onSystemAction(action) },
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
    val walletId = state.request.walletId
    if (walletId != null && state.failure.canPairAgain) {
        MoonAccentButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            text = stringResource(Localization.ledger_mc_pair_again),
            buttonColors = ButtonColorsSecondary,
            size = ButtonSizeLarge,
            onClick = { onPairAgain(walletId) },
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
    if (state.canRetry) {
        MoonAccentButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            text = stringResource(Localization.try_again),
            size = ButtonSizeLarge,
            onClick = onRetry,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

internal val LedgerSystemAction.label: Int
    get() = when (this) {
        LedgerSystemAction.EnableBluetooth -> Localization.ledger_mc_turn_on_bluetooth
        LedgerSystemAction.OpenBluetoothSettings -> Localization.ledger_mc_open_bluetooth_settings
        LedgerSystemAction.EnableLocation -> Localization.ledger_mc_turn_on_location
        LedgerSystemAction.OpenAppSettings -> Localization.ledger_mc_open_settings
        LedgerSystemAction.OpenLedgerLive -> Localization.ledger_mc_open_ledger_live
    }

@Preview
@Composable
private fun LedgerProgressOpenAppPreview() {
    ThemedPreview {
        LedgerProgressSheet(
            view = LedgerProgressView(PREVIEW_ACTIVE).next(LedgerOperationState.Active(PREVIEW_REQUEST, LedgerProgress.OpeningApp(LedgerAppId.Ton))),
            onClose = {},
            onRetry = {},
            onSystemAction = {},
        )
    }
}

@Preview
@Composable
private fun LedgerProgressBatchPreview() {
    ThemedPreview {
        LedgerProgressSheet(
            view = LedgerProgressView(PREVIEW_ACTIVE).next(
                LedgerOperationState.Active(
                    PREVIEW_REQUEST,
                    LedgerProgress.Confirm(LedgerAppId.Ethereum, LedgerConfirmKind.Transaction, step = 2, totalSteps = 3),
                ),
            ),
            onClose = {},
            onRetry = {},
            onSystemAction = {},
        )
    }
}

@Preview
@Composable
private fun LedgerProgressFailedPreview() {
    ThemedPreview {
        LedgerProgressSheet(
            view = LedgerProgressView(PREVIEW_ACTIVE)
                .next(LedgerOperationState.Active(PREVIEW_REQUEST, LedgerProgress.Confirm(LedgerAppId.Ton, LedgerConfirmKind.Transaction)))
                .next(LedgerOperationState.Failed(PREVIEW_REQUEST, LedgerFailure.SettingRequired(LedgerAppId.Ton, LedgerAppSetting.BlindSigning), canRetry = true)),
            onClose = {},
            onRetry = {},
            onSystemAction = {},
        )
    }
}

private val PREVIEW_REQUEST = LedgerRequest("00:11:22:33:44:55", "Stax 1A2B", LedgerPurpose.SignTransaction)
private val PREVIEW_ACTIVE = LedgerOperationState.Active(PREVIEW_REQUEST, LedgerProgress.Connecting)
