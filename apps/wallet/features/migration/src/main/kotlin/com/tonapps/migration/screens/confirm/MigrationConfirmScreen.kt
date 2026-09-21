package com.tonapps.migration.screens.confirm

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.core.components.Emoji
import com.tonapps.core.extensions.formatFiat
import com.tonapps.deposit.multicoin.screens.confirm.FeeMethodPickerLeading
import com.tonapps.deposit.multicoin.screens.confirm.FeeMethodPickerOption
import com.tonapps.deposit.multicoin.screens.confirm.FeeMethodPickerSheet
import com.tonapps.icu.Coins
import com.tonapps.migration.components.MigrationHowItWorksDialog
import com.tonapps.migration.data.MigrationFeeShortage
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.features.events.components.legacy.EventItem
import com.tonapps.wallet.features.events.components.legacy.UiEvent
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import kotlinx.coroutines.launch
import ui.components.moon.MoonDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonTopAppBar
import ui.components.moon.MoonTopAppBarSubtitle
import ui.components.moon.MoonTopAppBarTitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.MoonSlideConfirmation
import ui.components.moon.cell.MoonSlideConfirmationState
import ui.components.moon.cell.rememberSliderState
import ui.components.moon.container.MoonScaffold
import ui.components.moon.container.MoonSurface
import ui.components.moon.screen.MoonEmptyScreen
import ui.painterResource
import ui.theme.UIKit

private const val ConfirmOverlayFadeDurationMs = 180

@Composable
fun MigrationConfirmScreen(
    feature: MigrationConfirmFeature,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onMigrationSucceeded: () -> Unit,
    onDepositForFees: (WalletEntity, MigrationFeeShortage) -> Unit,
    onDepositForFeeOption: (WalletEntity, MigrationFeeOptionIcon) -> Unit,
) {
    val uiState by feature.uiState.collectAsState()

    when (val state = uiState) {
        MigrationConfirmUiState.Loading -> MoonSurface {
            MoonLoaderCell()
        }

        MigrationConfirmUiState.Error -> MoonEmptyScreen(
            text = stringResource(Localization.something_went_wrong),
        )

        is MigrationConfirmUiState.Content -> MigrationConfirmContent(
            feature = feature,
            data = state.data,
            onBack = onBack,
            onClose = onClose,
            onMigrationSucceeded = onMigrationSucceeded,
            onConfirm = feature::confirm,
            onRetry = feature::retry,
            onDismissFeeShortage = feature::dismissFeeShortage,
            onDepositForFees = { shortage ->
                onDepositForFees(state.data.prepareResult.wallet.wallet, shortage)
            },
            onDepositForFeeOption = { option ->
                onDepositForFeeOption(state.data.prepareResult.wallet.wallet, option)
            },
        )
    }
}

@Composable
private fun MigrationConfirmContent(
    feature: MigrationConfirmFeature,
    data: MigrationConfirmData,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onMigrationSucceeded: () -> Unit,
    onConfirm: (Context) -> Unit,
    onRetry: (Context) -> Unit,
    onDismissFeeShortage: () -> Unit,
    onDepositForFees: (MigrationFeeShortage) -> Unit,
    onDepositForFeeOption: (MigrationFeeOptionIcon) -> Unit,
) {
    val currency = feature.currency
    val hiddenBalances = feature.hiddenBalances
    val prepareResult = data.prepareResult
    val canConfirmAction = data.canConfirm

    val showFeeShortageDialog by feature.showFeeShortageDialog.collectAsState()
    val feePickerKind by feature.feePickerKind.collectAsState()
    val signingState by feature.signingState.collectAsState()
    val sentTransactions by feature.sentTransactions.collectAsState()
    val totalTransactions by feature.totalTransactions.collectAsState()
    val errorMessage by feature.errorMessage.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val sliderState = rememberSliderState()
    var isHowItWorksVisible by remember { mutableStateOf(false) }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    var bottomOverlayHeight by remember { mutableStateOf(0.dp) }
    val isLayoutReady = bottomOverlayHeight > 0.dp
    val needsScroll by remember {
        derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
    }
    val isAtBottom by remember {
        derivedStateOf { !listState.canScrollForward }
    }
    val canConfirm = !isLayoutReady || !needsScroll || isAtBottom
    val showScrollHint = isLayoutReady && needsScroll && !isAtBottom
    val isSuccess = signingState == MigrationSigningState.Success

    BackHandler(enabled = isSuccess) {
        onClose()
    }

    LaunchedEffect(signingState) {
        when (signingState) {
            MigrationSigningState.Success -> onMigrationSucceeded()

            MigrationSigningState.Idle,
            MigrationSigningState.Failed,
                -> sliderState.reset()

            MigrationSigningState.Sending -> Unit
        }
    }

    val confirmationState = when (signingState) {
        MigrationSigningState.Idle,
        MigrationSigningState.Failed,
            -> MoonSlideConfirmationState.Slider

        MigrationSigningState.Sending -> MoonSlideConfirmationState.Loader
        MigrationSigningState.Success -> MoonSlideConfirmationState.Done
    }
    val sendingTitle = when {
        totalTransactions > 1 -> {
            val progress = (sentTransactions * 100) / totalTransactions
            "${stringResource(Localization.migration_confirm_sending_transactions)} $progress%"
        }

        else -> stringResource(Localization.sending_transaction)
    }
    val totalValue = formatMigrationTotal(
        currencyCode = currency.code,
        totalEquivalent = prepareResult.totalEquivalent,
        nftCount = prepareResult.nftCount,
    )

    val backgroundColor = UIKit.colorScheme.background.page
    val fadeStops = remember(backgroundColor) {
        arrayOf(0f to Color.Transparent, 0.3f to backgroundColor)
    }
    val overlayFadeAlpha by animateFloatAsState(
        targetValue = if (showScrollHint) {
            1f
        } else {
            0f
        },
        animationSpec = tween(durationMillis = ConfirmOverlayFadeDurationMs),
        label = "confirmOverlayFade",
    )
    val overlayFadeSpec = tween<Float>(durationMillis = ConfirmOverlayFadeDurationMs)

    MoonScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MoonTopAppBar(
                title = { MoonTopAppBarTitle(text = stringResource(Localization.confirm_action)) },
                subtitle = {
                    MigrationConfirmSubtitle(wallet = prepareResult.destinationWallet)
                },
                navigationIconRes = UIKitIcon.ic_chevron_left_16,
                onNavigationClick = if (isSuccess) {
                    onClose
                } else {
                    onBack
                },
                actionIconRes = UIKitIcon.ic_close_16,
                onActionClick = onClose,
                ignoreSystemOffset = true,
                showDivider = false,
                backgroundColor = Color.Transparent,
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(rememberNestedScrollInteropConnection()),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = 32.dp + bottomOverlayHeight,
                ),
            ) {
                migrationEmulationItems(
                    items = data.emulationItems,
                    hiddenBalances = hiddenBalances,
                )
                if (data.tonFee != null || data.tronFee != null) {
                    item(key = "migration_fees", contentType = "migration_fees") {
                        Spacer(Modifier.height(8.dp))
                        MoonBundleCell {
                            Column {
                                data.tonFee?.let { fee ->
                                    MigrationFeeCell(
                                        title = stringResource(Localization.migration_confirm_ton_fee),
                                        fee = fee,
                                        canSwitchFee = data.canSwitchTonFee,
                                        onClick = feature::openTonFeePicker,
                                    )
                                }
                                if (data.tonFee != null && data.tronFee != null) {
                                    MoonDivider()
                                }
                                data.tronFee?.let { fee ->
                                    MigrationFeeCell(
                                        title = stringResource(Localization.migration_confirm_trc20_fee),
                                        fee = fee,
                                        canSwitchFee = data.canSwitchTronFee,
                                        onClick = feature::openTronFeePicker,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = overlayFadeAlpha }
                        .background(brush = Brush.verticalGradient(colorStops = fadeStops)),
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedVisibility(
                        visible = showScrollHint,
                        enter = fadeIn(overlayFadeSpec),
                        exit = fadeOut(overlayFadeSpec),
                    ) {
                        ScrollConfirmHint(
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(
                                        index = listState.layoutInfo.totalItemsCount - 1,
                                        scrollOffset = listState.layoutInfo.viewportSize.height,
                                    )
                                }
                            },
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                bottomOverlayHeight = with(density) {
                                    coordinates.size.height.toDp()
                                }
                            }
                            .padding(bottom = 16.dp + bottomInset),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MoonSlideConfirmation(
                            state = confirmationState,
                            sliderState = sliderState,
                            title = when (confirmationState) {
                                MoonSlideConfirmationState.Done -> stringResource(Localization.done)
                                MoonSlideConfirmationState.Loader -> sendingTitle
                                MoonSlideConfirmationState.Slider -> stringResource(Localization.confirm)
                            },
                            subtitle = if (confirmationState == MoonSlideConfirmationState.Slider) {
                                stringResource(Localization.swipe_right)
                            } else {
                                null
                            },
                            error = if (signingState == MigrationSigningState.Failed) {
                                errorMessage
                                    ?: stringResource(Localization.migration_confirm_transaction_failed)
                            } else {
                                null
                            },
                            buttonTitle = stringResource(Localization.try_again),
                            enabled = canConfirm &&
                                canConfirmAction &&
                                signingState == MigrationSigningState.Idle,
                            loaderColor = UIKit.colorScheme.accent.blue,
                            onConfirm = { onConfirm(context) },
                            onClick = { onRetry(context) },
                            onDisabledClick = if (!canConfirmAction && data.feeShortage != null) {
                                feature::showFeeShortage
                            } else {
                                null
                            },
                            onDone = onClose,
                        )

                        MigrationTotalRow(
                            totalValue = totalValue,
                            onClick = { isHowItWorksVisible = true },
                        )
                    }
                }
            }
        }
    }

    if (isHowItWorksVisible) {
        MigrationHowItWorksDialog(onClose = { isHowItWorksVisible = false })
    }

    when (feePickerKind) {
        MigrationFeePickerKind.Ton -> FeeMethodPickerSheet(
            options = data.tonFeeOptions.map { it.toPickerOption() },
            selectedId = data.selectedTonFeeId,
            onSelect = feature::selectTonFeeOption,
            onClose = feature::dismissFeePicker,
            onActionSelect = { optionId ->
                feature.dismissFeePicker()
                data.tonFeeOptions.firstOrNull { it.id == optionId }?.let { option ->
                    if (option.icon == MigrationFeeOptionIcon.Battery) {
                        feature.onBatteryRechargeOpened()
                    }
                    onDepositForFeeOption(option.icon)
                }
            },
        )

        MigrationFeePickerKind.Tron -> FeeMethodPickerSheet(
            options = data.tronFeeOptions.map { it.toPickerOption() },
            selectedId = data.selectedTronFeeId,
            onSelect = feature::selectTronFeeOption,
            onClose = feature::dismissFeePicker,
            onActionSelect = { optionId ->
                feature.dismissFeePicker()
                data.tronFeeOptions.firstOrNull { it.id == optionId }?.let { option ->
                    if (option.icon == MigrationFeeOptionIcon.Battery) {
                        feature.onBatteryRechargeOpened()
                    }
                    onDepositForFeeOption(option.icon)
                }
            },
        )

        null -> Unit
    }

    val shortage = data.feeShortage
    if (showFeeShortageDialog && shortage != null) {
        shortage.InsufficientFundsDialog(
            onDeposit = {
                onDismissFeeShortage()
                if (shortage is MigrationFeeShortage.Battery) {
                    feature.onBatteryRechargeOpened()
                }
                onDepositForFees(shortage)
            },
            onClose = onDismissFeeShortage,
        )
    }
}

private fun LazyListScope.migrationEmulationItems(
    items: List<UiEvent.Item>,
    hiddenBalances: Boolean,
) {
    items(
        items = items,
        key = { it.id },
        contentType = { "migration_emulation" },
    ) { item ->
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            EventItem(
                event = item,
                hiddenBalances = hiddenBalances,
                enabled = false,
                onClick = { _, _ -> },
            )
        }
    }
}

@Composable
private fun MigrationTotalRow(
    totalValue: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        MoonItemSubtitle(
            text = stringResource(Localization.total, totalValue),
            color = UIKit.colorScheme.text.secondary,
        )
        Spacer(Modifier.padding(start = 4.dp))
        MoonItemIcon(
            painter = painterResource(UIKitIcon.ic_information_circle_16),
            color = UIKit.colorScheme.icon.secondary,
        )
    }
}

@Composable
private fun MigrationConfirmSubtitle(wallet: McWalletEntity) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoonTopAppBarSubtitle(text = stringResource(Localization.migration_confirm_subtitle))
        Emoji(emoji = wallet.emoji, size = 16.dp)
        MoonTopAppBarSubtitle(text = wallet.name)
    }
}

@Composable
private fun MigrationFeeOptionUi.toPickerOption(): FeeMethodPickerOption {
    return FeeMethodPickerOption(
        id = id,
        title = titleRes?.let { stringResource(it) } ?: title.orEmpty(),
        subtitle = batteryCharges?.let { charges ->
            pluralStringResource(Plurals.battery_charges, charges, charges)
        } ?: subtitle?.toString().orEmpty(),
        leading = when (icon) {
            MigrationFeeOptionIcon.Battery -> FeeMethodPickerLeading.Battery
            MigrationFeeOptionIcon.Ton -> FeeMethodPickerLeading.Image(
                url = TokenEntity.TON.imageUri.toString(),
            )
            MigrationFeeOptionIcon.Trx -> FeeMethodPickerLeading.Image(
                url = TokenEntity.TRX.imageUri.toString(),
            )
        },
        enabled = enough,
        actionTitle = if (enough) {
            null
        } else {
            stringResource(Localization.deposit)
        },
    )
}

@Composable
private fun MigrationFeeCell(
    title: String,
    fee: MigrationFeeUi,
    canSwitchFee: Boolean,
    onClick: () -> Unit,
) {
    val accentColor = UIKit.colorScheme.text.accent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = canSwitchFee,
                onClick = onClick,
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MoonItemTitle(
            text = title,
            color = UIKit.colorScheme.text.secondary,
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.End),
        ) {
            when (fee) {
                is MigrationFeeUi.Battery -> {
                    val chargesText = pluralStringResource(
                        Plurals.battery_charges,
                        fee.charges,
                        fee.charges,
                    )
                    MoonItemTitle(text = "$chargesText · ")
                    MoonItemTitle(
                        text = stringResource(Localization.battery),
                        color = accentColor,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                is MigrationFeeUi.Native -> {
                    MoonItemTitle(
                        text = buildAnnotatedString {
                            append("≈ ")
                            append(fee.fiatFormatted)
                            append(" · ")
                        },
                    )
                    MoonItemTitle(
                        text = fee.symbol,
                        color = accentColor,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            if (canSwitchFee) {
                MoonItemIcon(
                    painter = painterResource(UIKitIcon.ic_switch_16),
                    color = UIKit.colorScheme.text.accent,
                    size = 16.dp,
                )
            }
        }
    }
}

@Composable
private fun ScrollConfirmHint(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .defaultMinSize(minHeight = 36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(UIKit.colorScheme.buttonTertiary.primaryBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MoonItemIcon(
            painter = painterResource(UIKitIcon.ic_arrow_down_16),
            color = UIKit.colorScheme.buttonTertiary.primaryForeground,
            size = 16.dp,
        )
        Text(
            text = stringResource(Localization.send_transaction_scroll),
            style = UIKit.typography.label2,
            color = UIKit.colorScheme.buttonTertiary.primaryForeground,
        )
    }
}

@Composable
private fun formatMigrationTotal(
    currencyCode: String,
    totalEquivalent: Float?,
    nftCount: Int,
): String {
    val fiat = totalEquivalent?.let { amount ->
        Coins.of(amount.toDouble(), decimals = 2).formatFiat(currencyCode)
    }
    if (fiat == null) {
        return if (nftCount <= 0) {
            ""
        } else {
            pluralStringResource(Plurals.nft_count, nftCount, nftCount)
        }
    }
    if (nftCount <= 0) {
        return fiat
    }
    val nfts = pluralStringResource(Plurals.nft_count, nftCount, nftCount)
    return "$fiat + $nfts"
}
