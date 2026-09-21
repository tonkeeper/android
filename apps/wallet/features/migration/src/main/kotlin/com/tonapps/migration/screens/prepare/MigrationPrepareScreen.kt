package com.tonapps.migration.screens.prepare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tonapps.migration.components.MigratableWalletCell
import com.tonapps.migration.components.MigrationHowItWorksDialog
import com.tonapps.migration.components.MigrationHowItWorksLink
import com.tonapps.migration.data.MigratableWallet
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.ButtonColorsPrimary
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.ButtonSizeSmall
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonBottomBar
import ui.components.moon.MoonTextShimmer
import ui.components.moon.MoonTopAppBar
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.cell.defaultBundleType
import ui.components.moon.container.MoonScaffold
import ui.components.moon.moonBottomBarHeight
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.moon.screen.MoonEmptyScreenType
import ui.painterResource
import ui.theme.Dimens
import ui.theme.UIKit
import ui.theme.modifiers.rememberShimmerPhase

private const val SHIMMER_ROW_COUNT = 5

@Composable
fun MigrationPrepareScreen(
    feature: MigrationPrepareFeature,
    onClose: () -> Unit,
    onAddWallet: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToConfirm: (String) -> Unit,
    onSkip: (() -> Unit)? = null,
) {
    val wallets by feature.wallets.collectAsState()
    val selectedWalletId by feature.selectedWalletId.collectAsState()
    val currencyCode by feature.currencyCode.collectAsState()
    val isLoading by feature.isLoading.collectAsState()
    val isFailed by feature.isFailed.collectAsState()
    val isPreparing by feature.isPreparing.collectAsState()

    when {
        isFailed -> MigrationPrepareError(
            onClose = onClose,
            onRetry = feature::retry,
        )

        !isLoading && wallets.isEmpty() -> MigrationPrepareEmpty(
            onClose = onClose,
            onAddWallet = onAddWallet,
        )

        else -> MigrationPrepareContent(
            wallets = wallets,
            selectedWalletId = selectedWalletId,
            currencyCode = currencyCode,
            isLoading = isLoading,
            isPreparing = isPreparing,
            onSelectWallet = feature::selectWallet,
            onClose = onClose,
            onContinue = feature::continueClicked,
            onSkip = onSkip,
        )
    }

    LaunchedEffect(feature) {
        feature.events.collect { event ->
            when (event) {
                MigrationPrepareEvent.NeedBackup -> onNavigateToBackup()
                is MigrationPrepareEvent.Ready -> onNavigateToConfirm(event.walletId)
            }
        }
    }
}

@Composable
private fun MigrationSkipBar(
    onSkip: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.heightBar)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoonAccentButton(
            text = stringResource(Localization.setup_finish_skip),
            size = ButtonSizeSmall,
            buttonColors = ButtonColorsSecondary,
            onClick = onSkip,
        )
    }
}

@Composable
private fun MigrationPrepareError(
    onClose: () -> Unit,
    onRetry: () -> Unit,
) {
    MoonScaffold(
        modifier = Modifier.fillMaxSize(),
        title = "",
        onClose = onClose,
    ) {
        MoonEmptyScreen(
            type = MoonEmptyScreenType.Error,
            text = stringResource(Localization.something_went_wrong),
            description = stringResource(Localization.could_not_load_content),
            buttonText = stringResource(Localization.retry),
            onButtonClick = onRetry,
        )
    }
}

@Composable
private fun MigratableWalletListShimmer() {
    val fill = UIKit.colorScheme.background.contentTint
    val highlight = UIKit.colorScheme.background.highlighted
    val shimmerPhase by rememberShimmerPhase()

    repeat(SHIMMER_ROW_COUNT) { index ->
        MoonBundleCell(position = defaultBundleType(SHIMMER_ROW_COUNT, index)) {
            MigratableWalletRowShimmer(
                fill = fill,
                highlight = highlight,
                phase = shimmerPhase,
            )
        }
    }
}

@Composable
private fun MigratableWalletRowShimmer(
    fill: Color,
    highlight: Color,
    phase: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 76.dp)
            .padding(horizontal = Dimens.offsetMedium, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(fill),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Top,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MoonTextShimmer(
                    text = "Wallet Name",
                    style = UIKit.typography.label1,
                    phase = phase,
                    cornerRadius = 12.dp,
                    backgroundFill = fill,
                    highlightColor = highlight,
                )
                MoonTextShimmer(
                    text = "Check",
                    style = UIKit.typography.label1,
                    phase = phase,
                    cornerRadius = 12.dp,
                    backgroundFill = fill,
                    highlightColor = highlight,
                )
            }
            Spacer(Modifier.height(4.dp))
            MoonTextShimmer(
                text = "$1,234.56 + 2 NFTs",
                style = UIKit.typography.body2,
                phase = phase,
                cornerRadius = 8.dp,
                backgroundFill = fill,
                highlightColor = highlight,
            )
        }
    }
}

@Composable
private fun MigrationPrepareEmpty(
    onClose: () -> Unit,
    onAddWallet: () -> Unit,
) {
    MoonScaffold(
        modifier = Modifier.fillMaxSize(),
        title = "",
        onClose = onClose,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(UIKitIcon.ic_download_28),
                contentDescription = null,
                tint = UIKit.colorScheme.accent.blue,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Localization.migration_empty_title),
                style = UIKit.typography.h3,
                color = UIKit.colorScheme.text.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Localization.migration_empty_description),
                style = UIKit.typography.body1,
                color = UIKit.colorScheme.text.secondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            MoonAccentButton(
                text = stringResource(Localization.migration_add_ton_wallet),
                size = ButtonSizeSmall,
                buttonColors = ButtonColorsSecondary,
                onClick = onAddWallet,
            )
        }
    }
}

@Composable
private fun MigrationPrepareContent(
    wallets: List<MigratableWallet>,
    selectedWalletId: String?,
    currencyCode: String,
    isLoading: Boolean,
    isPreparing: Boolean,
    onSelectWallet: (String) -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit,
    onSkip: (() -> Unit)? = null,
) {
    var isInfoVisible by remember { mutableStateOf(false) }

    MoonScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (onSkip == null) {
                MoonTopAppBar(
                    title = "",
                    actionIconRes = UIKitIcon.ic_close_16,
                    onActionClick = onClose,
                    ignoreSystemOffset = true,
                    showDivider = false,
                    backgroundColor = Color.Transparent,
                )
            } else {
                MigrationSkipBar(onSkip = onSkip)
            }
        },
    ) {
        val bottomOverlayHeight = moonBottomBarHeight()

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .nestedScroll(rememberNestedScrollInteropConnection())
                    .padding(bottom = bottomOverlayHeight),
            ) {
                MoonTextContentCell(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = stringResource(Localization.migration_prepare_title),
                    description = stringResource(Localization.migration_prepare_description),
                    titleStyle = UIKit.typography.h2,
                )
                Spacer(Modifier.height(4.dp))
                MigrationHowItWorksLink(
                    onClick = { isInfoVisible = true },
                )
                Spacer(Modifier.height(24.dp))

                if (isLoading) {
                    MigratableWalletListShimmer()
                } else {
                    wallets.forEachIndexed { index, wallet ->
                        MigratableWalletCell(
                            wallet = wallet,
                            currencyCode = currencyCode,
                            selected = wallet.wallet.id == selectedWalletId,
                            position = MoonBundlePosition.default(wallets.size, index),
                            onClick = { onSelectWallet(wallet.wallet.id) },
                        )
                    }
                }
            }

            MoonBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                MoonAccentButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(Localization.continue_action),
                    size = ButtonSizeLarge,
                    buttonColors = ButtonColorsPrimary,
                    enabled = !isLoading && selectedWalletId != null,
                    loading = isPreparing,
                    onClick = onContinue,
                )
            }
        }
    }

    if (isInfoVisible) {
        MigrationHowItWorksDialog(onClose = { isInfoVisible = false })
    }
}
