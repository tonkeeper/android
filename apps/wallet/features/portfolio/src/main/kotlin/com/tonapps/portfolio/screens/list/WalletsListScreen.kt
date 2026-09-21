package com.tonapps.portfolio.screens.list

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tonapps.blockchain.model.legacy.Wallet
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.MysteryRaffle.MysteryRaffleSource
import com.tonapps.core.components.BannersViewer
import com.tonapps.core.components.Emoji
import com.tonapps.core.deeplink.withRaffleSource
import com.tonapps.portfolio.wallet.CommonWallet
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletType
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.delay
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeSmall
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
import ui.components.moon.MoonTextShimmer
import ui.components.moon.MoonTopAppBar
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.TextCell
import ui.components.moon.cell.defaultBundleType
import ui.components.moon.container.MoonScaffold
import ui.components.popup.MoonAnchorTooltip
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit
import ui.theme.modifiers.rememberShimmerPhase

private const val BALANCE_SHIMMER_PLACEHOLDER = "$0.00"

@Composable
fun WalletsListScreen(
    feature: WalletsListFeature,
    onAddWalletClick: () -> Unit,
    onSelectWallet: (walletId: String) -> Unit,
    onOpenLink: (String) -> Unit,
    onEditWallet: (walletId: String) -> Unit,
    onClose: () -> Unit,
) {
    val wallets by feature.wallets.collectAsState()
    val selectedId by feature.selectedWalletId.collectAsState()
    val banners by feature.banners.collectAsState()
    val editMode by feature.editMode.collectAsState()
    val hiddenBalances by feature.hiddenBalances.collectAsState()
    val shimmerPhase by rememberShimmerPhase()
    val haptic = LocalHapticFeedback.current
    val hostView = LocalView.current

    val listState = rememberLazyListState()
    var addTooltipVisible by remember { mutableStateOf(false) }
    val addWalletItemVisible by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == "add_wallet" } }
    }
    LaunchedEffect(addWalletItemVisible, editMode) {
        if (addWalletItemVisible && !editMode && feature.consumeAddMcTooltip()) {
            addTooltipVisible = true
        }
    }
    LaunchedEffect(addTooltipVisible) {
        if (addTooltipVisible) {
            try {
                delay(5000)
            } finally {
                addTooltipVisible = false
            }
        }
    }
    val orderedWallets = remember { mutableStateListOf<CommonWallet>().apply { addAll(feature.wallets.value) } }
    val dragDropState = rememberWalletsDragDropState(
        listState = listState,
        onMove = { fromWalletId, toWalletId ->
            val fromIndex = orderedWallets.indexOfFirst { it.id == fromWalletId }
            val toIndex = orderedWallets.indexOfFirst { it.id == toWalletId }
            if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                orderedWallets.add(toIndex, orderedWallets.removeAt(fromIndex))
                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
        },
        onDragEnd = {
            val orderedIds = orderedWallets.map { it.id }
            val reconciled = feature.wallets.value.sortedBy { wallet ->
                orderedIds.indexOf(wallet.id).takeIf { it != -1 } ?: Int.MAX_VALUE
            }
            orderedWallets.clear()
            orderedWallets.addAll(reconciled)
            feature.saveWalletsOrder(reconciled.map { it.id })
        },
    )
    LaunchedEffect(wallets) {
        if (dragDropState.draggingWalletId == null) {
            orderedWallets.clear()
            orderedWallets.addAll(wallets)
        }
    }
    LaunchedEffect(editMode) {
        if (!editMode) {
            dragDropState.onDragInterrupted()
        }
    }

    val raffleEvents = AnalyticsHelper.Default.events.mysteryRaffle
    LaunchedEffect(banners.isNotEmpty()) {
        if (banners.isNotEmpty()) {
            raffleEvents.raffleBannerView(MysteryRaffleSource.WalletsList)
        }
    }

    // TODO TK-2492: move the status-bar height cap to a shared uikit solution for Compose modals
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        MoonScaffold(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = this.maxHeight - topInset)
                .navigationBarsPadding(),
            topBar = {
                MoonTopAppBar(
                    title = stringResource(Localization.wallets_list),
                    titleAutoShrink = true,
                    actionIconRes = UIKitIcon.ic_close_16,
                    onActionClick = onClose,
                    navigation = {
                        MoonAccentButton(
                            text = stringResource(if (editMode) { Localization.done } else { Localization.edit }),
                            size = ButtonSizeSmall,
                            buttonColors = ButtonColorsSecondary,
                            onClick = { feature.toggleEditMode() },
                        )
                    },
                    ignoreSystemOffset = true,
                    showDivider = false,
                    backgroundColor = Color.Transparent,
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .nestedScroll(rememberNestedScrollInteropConnection())
                    .onGloballyPositioned(dragDropState::registerContainer)
                    .pointerInput(editMode) {
                        if (!editMode) {
                            return@pointerInput
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            val walletId = dragDropState.findWalletIdByHandleAt(down.position)
                                ?: return@awaitEachGesture
                            hostView.parent?.requestDisallowInterceptTouchEvent(true)
                            dragDropState.onDragStart(walletId)
                            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            var lastY = down.position.y
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    break
                                }
                                change.consume()
                                dragDropState.onDrag(change.position.y - lastY)
                                lastY = change.position.y
                            }
                            dragDropState.onDragInterrupted()
                        }
                    },
            ) {
                if (banners.isNotEmpty()) {
                    item(key = "banners") {
                        BannersViewer(
                            banners = banners,
                            onClick = { button ->
                                raffleEvents.raffleBannerClick(MysteryRaffleSource.WalletsList)
                                onOpenLink(button.payload.withRaffleSource(MysteryRaffleSource.WalletsList.key))
                            },
                            onHide = { banner ->
                                raffleEvents.raffleBannerDismiss(MysteryRaffleSource.WalletsList)
                                feature.hideBanner(banner)
                            },
                        )
                    }
                }
                itemsIndexed(
                    items = orderedWallets,
                    key = { _, w -> w.id },
                    contentType = { _, _ -> WALLET_ROW_CONTENT_TYPE },
                ) { index, wallet ->
                    val isDragging = wallet.id == dragDropState.draggingWalletId
                    WalletRowCell(
                        modifier = if (isDragging) {
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer { translationY = dragDropState.draggingItemOffset }
                        } else {
                            Modifier.animateItem()
                        },
                        wallet = wallet,
                        selected = wallet.id == selectedId,
                        balance = feature.balances[wallet.id],
                        hiddenBalance = hiddenBalances,
                        editMode = editMode,
                        shimmerPhase = shimmerPhase,
                        position = defaultBundleType(orderedWallets.size, index),
                        onClick = { onSelectWallet(wallet.id) },
                        onEditClick = { onEditWallet(wallet.id) },
                        dragHandle = { WalletDragHandle(walletId = wallet.id, dragDropState = dragDropState) },
                    )
                }
                if (orderedWallets.isNotEmpty()) {
                    item(key = "add_wallet") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box {
                                MoonAccentButton(
                                    text = stringResource(Localization.add_wallet),
                                    size = ButtonSizeSmall,
                                    buttonColors = ButtonColorsSecondary,
                                    onClick = onAddWalletClick
                                )
                                MoonAnchorTooltip(
                                    expanded = addTooltipVisible && !editMode,
                                    onDismissRequest = { addTooltipVisible = false },
                                    text = stringResource(Localization.tooltip_add_multichain_wallet),
                                    badge = stringResource(Localization.badge_new),
                                    backgroundColor = UIKit.colorScheme.accent.blue,
                                    textColor = Color.White,
                                    maxWidth = 288.dp,
                                    preferAbove = true,
                                    centerTail = true,
                                    onClick = {
                                        addTooltipVisible = false
                                        onAddWalletClick()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WalletRowCell(
    wallet: CommonWallet,
    selected: Boolean,
    balance: CharSequence?,
    hiddenBalance: Boolean,
    editMode: Boolean,
    shimmerPhase: Float,
    position: MoonBundlePosition,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: (@Composable () -> Unit)? = null,
) {
    Box(modifier = modifier) {
        MoonBundleCell(position = position) {
            TextCell(
                title = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MoonItemTitle(text = wallet.name, maxLines = 1)
                        WalletTags(wallet = wallet)
                    }
                },
                subtitle = {
                    when {
                        hiddenBalance -> MoonItemSubtitle(text = HIDDEN_BALANCE, maxLines = 1)
                        balance != null -> MoonItemSubtitle(text = balance, maxLines = 1)
                        else -> MoonTextShimmer(
                            text = BALANCE_SHIMMER_PLACEHOLDER,
                            style = UIKit.typography.body2,
                            phase = shimmerPhase,
                        )
                    }
                },
                image = {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(wallet.color)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Emoji(emoji = wallet.emoji)
                    }
                },
                content = when {
                    editMode -> {
                        {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MoonItemIcon(
                                    painter = painterResource(UIKitIcon.ic_pencil_outline_28),
                                    color = UIKit.colorScheme.icon.secondary,
                                    onClick = onEditClick,
                                )
                                dragHandle?.invoke()
                            }
                        }
                    }
                    selected -> {
                        {
                            MoonItemIcon(
                                painter = painterResource(UIKitIcon.ic_donemark_otline_28),
                                color = UIKit.colorScheme.accent.blue,
                            )
                        }
                    }
                    else -> null
                },
                onClick = when {
                    editMode -> null
                    else -> onClick
                },
                minHeight = 76.dp,
            )
        }
    }
}

@Composable
private fun WalletDragHandle(walletId: String, dragDropState: WalletsDragDropState) {
    val handleTouchPadding = with(LocalDensity.current) { 12.dp.toPx() }
    DisposableEffect(walletId, dragDropState) {
        onDispose { dragDropState.unregisterHandle(walletId) }
    }
    MoonItemIcon(
        painter = painterResource(UIKitIcon.ic_reorder_28),
        color = UIKit.colorScheme.icon.secondary,
        modifier = Modifier.onGloballyPositioned { coordinates ->
            dragDropState.registerHandle(walletId, coordinates.boundsInRoot().inflate(handleTouchPadding))
        },
    )
}

@Composable
private fun RowScope.WalletTags(wallet: CommonWallet) {
    val version = wallet.version
    if (version == WalletVersion.V5R1 || version == WalletVersion.V5BETA) {
        MoonLabel(
            text = stringResource(
                if (version == WalletVersion.V5BETA) { Localization.w5beta } else { Localization.w5 },
            ),
            colors = MoonLabelDefault.success(),
        )
    }
    val typeText = when (wallet.type) {
        WalletType.Multichain -> stringResource(Localization.multichain)
        WalletType.Watch -> stringResource(Localization.watch_only)
        WalletType.Testnet -> stringResource(Localization.testnet)
        WalletType.Signer, WalletType.SignerQR -> stringResource(Localization.signer)
        WalletType.Ledger -> stringResource(Localization.ledger)
        WalletType.Keystone -> stringResource(Localization.keystone)
        WalletType.Tetra -> stringResource(Localization.tetra)
        WalletType.Default, WalletType.Lockup -> null
    }
    if (typeText != null) {
        MoonLabel(
            text = typeText,
            colors = when (wallet.type) {
                WalletType.Multichain -> MoonLabelDefault.blue()
                else -> MoonLabelDefault.grey()
            },
        )
    }
}

@Preview
@Composable
private fun WalletRowCellPreview() {
    ThemedPreview(isDarkOnly = true) {
        Column {
            WalletRowCell(
                wallet = previewMultichainWallet(),
                selected = true,
                balance = "$1,234.56",
                hiddenBalance = false,
                editMode = false,
                shimmerPhase = 0f,
                position = MoonBundlePosition.Header,
                onClick = {},
                onEditClick = {},
            )
            WalletRowCell(
                wallet = previewLegacyWallet(),
                selected = false,
                balance = null,
                hiddenBalance = false,
                editMode = true,
                shimmerPhase = 0f,
                position = MoonBundlePosition.Footer,
                onClick = {},
                onEditClick = {},
                dragHandle = {
                    MoonItemIcon(
                        painter = painterResource(UIKitIcon.ic_reorder_28),
                        color = UIKit.colorScheme.icon.secondary,
                    )
                },
            )
        }
    }
}

private fun previewMultichainWallet(): CommonWallet = CommonWallet.Mc(
    McWalletEntity(
        credentialId = "preview",
        name = "Main Wallet",
        emoji = "😎",
        type = McWalletType.Multicoin,
        id = ""
    )
)

private fun previewLegacyWallet(): CommonWallet = CommonWallet.Legacy(
    WalletEntity.EMPTY.copy(
        id = "legacy",
        version = WalletVersion.V5R1,
        label = Wallet.Label(accountName = "Old Wallet", emoji = "🐱"),
    )
)
