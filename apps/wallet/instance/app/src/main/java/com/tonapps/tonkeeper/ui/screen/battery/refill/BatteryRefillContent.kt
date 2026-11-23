package com.tonapps.tonkeeper.ui.screen.battery.refill

import android.view.ContextThemeWrapper
import androidx.annotation.StyleRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.core.components.AccountCellShimmer
import com.tonapps.core.components.chainImageResourceUrl
import com.tonapps.extensions.activity
import com.tonapps.tonkeeper.extensions.clipboardText
import com.tonapps.tonkeeper.ui.screen.battery.refill.entity.PromoState
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import ui.components.moon.MoonActionIcon
import ui.components.moon.MoonChevronRight
import ui.components.moon.MoonCircleIcon
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.MoonTextFieldCell
import ui.components.moon.cell.TextCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.components.moon.dialog.rememberDialogNavigator
import ui.theme.UIKit
import uikit.widget.BatteryView
import kotlin.math.absoluteValue

private const val DISABLED_ALPHA = 0.48f
private const val SKELETON_ROWS = 3
internal val ROW_HEIGHT = 76.dp
private val METHOD_ICON_SIZE = 44.dp
private val CHAIN_BADGE_SIZE = 20.dp

data class BatteryRefillCallbacks(
    val onClose: () -> Unit,
    val onPackClick: (String) -> Unit,
    val onOtherClick: () -> Unit,
    val onPayWithClick: () -> Unit,
    val onTopUp: () -> Unit,
    val onGiftClick: () -> Unit,
    val onHistoryClick: () -> Unit,
    val onPromoSubmit: (String) -> Unit,
    val onRestoreClick: () -> Unit,
)

@Composable
fun BatteryRefillRoute(viewModel: BatteryRefillViewModel, onClose: () -> Unit) {
    val state by viewModel.uiStateFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var payWithShown by rememberSaveable { mutableStateOf(false) }
    val navigator = rememberDialogNavigator(onClose = { payWithShown = false })
    val shown = state
    if (shown == null) {
        BatteryRefillLoading(onClose = onClose)
        return
    }

    BatteryRefillContent(
        state = shown,
        callbacks = BatteryRefillCallbacks(
            onClose = onClose,
            onPackClick = viewModel::selectPack,
            onOtherClick = viewModel::openOther,
            onPayWithClick = { payWithShown = true },
            onTopUp = { context.activity?.let(viewModel::topUp) },
            onGiftClick = viewModel::openGift,
            onHistoryClick = viewModel::openHistory,
            onPromoSubmit = viewModel::submitPromo,
            onRestoreClick = viewModel::restorePurchases,
        ),
    )

    if (payWithShown) {
        BatteryPayWithSheet(
            navigator = navigator,
            methods = shown.methods,
            enabled = !shown.busy,
            onSelect = { row ->
                if (row.kind == PayMethodKind.Asset) {
                    viewModel.openAsset(row.id)
                } else {
                    viewModel.selectMethod(row.id)
                }
            },
        )
    }
}

@Composable
fun BatteryRefillContent(
    state: BatteryRefillUiState,
    callbacks: BatteryRefillCallbacks,
    modifier: Modifier = Modifier,
) {
    val button = state.button
    val density = LocalDensity.current
    var buttonAreaHeight by remember { mutableStateOf(0.dp) }
    val bottomPadding = if (button != null) {
        buttonAreaHeight
    } else {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding),
        ) {
            item(key = "close") {
                CloseButton(onClick = callbacks.onClose)
            }
            item(key = "header") {
                BatteryHeader(state.header)
            }
            if (state.methodLoading) {
                packSkeletonItems()
            }
            itemsIndexed(state.packs, key = { _, pack -> "pack_${pack.id}" }) { index, pack ->
                PackCell(
                    pack = pack,
                    position = MoonBundlePosition.default(state.packs.size, index),
                    onClick = if (pack.isOther) {
                        callbacks.onOtherClick
                    } else {
                        { callbacks.onPackClick(pack.id) }
                    },
                )
            }
            if (!state.methodLoading && state.methods.isNotEmpty()) {
                item(key = "pay_with") {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        PayWithCell(
                            method = state.method,
                            enabled = !state.busy,
                            onClick = callbacks.onPayWithClick,
                        )
                    }
                }
            }
            item(key = "links") {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    if (state.giftVisible) {
                        LinkCell(
                            iconRes = R.drawable.ic_gift_44,
                            title = stringResource(Localization.battery_refill_gift),
                            subtitle = stringResource(Localization.battery_refill_gift_subtitle),
                            position = MoonBundlePosition.Header,
                            enabled = !state.busy,
                            onClick = callbacks.onGiftClick,
                        )
                    }
                    LinkCell(
                        iconRes = R.drawable.ic_battery_refund_44,
                        title = stringResource(Localization.battery_refund_title),
                        subtitle = stringResource(Localization.battery_refund_subtitle),
                        position = if (state.giftVisible) {
                            MoonBundlePosition.Footer
                        } else {
                            MoonBundlePosition.Default
                        },
                        enabled = !state.busy,
                        onClick = callbacks.onHistoryClick,
                    )
                }
            }
            state.promo?.let { promo ->
                item(key = "promo") {
                    PromoField(
                        promo = promo,
                        modifier = Modifier.padding(top = 16.dp),
                        onSubmit = callbacks.onPromoSubmit,
                    )
                }
            }
            item(key = "footer") {
                Footer(
                    chargingEnabled = state.chargingEnabled,
                    restoreEnabled = !state.busy,
                    onRestoreClick = callbacks.onRestoreClick,
                )
            }
        }

        if (button != null) {
            MoonBottomButtonCell(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { size -> buttonAreaHeight = with(density) { size.height.toDp() } }
                    .navigationBarsPadding()
                    .imePadding(),
                text = button.text,
                enabled = button.enabled,
                loading = state.busy,
                onClick = callbacks.onTopUp,
            )
        }
    }
}

@Composable
private fun BatteryRefillLoading(onClose: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
        ),
    ) {
        item(key = "close") {
            CloseButton(onClick = onClose)
        }
        item(key = "header") {
            BatteryHeader(header = null)
        }
        packSkeletonItems()
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        MoonActionIcon(
            painter = painterResource(UIKitIcon.ic_close_16),
            onClick = onClick,
            tintColor = UIKit.colorScheme.buttonSecondary.primaryForeground,
            contentDescription = stringResource(Localization.close),
        )
    }
}

private fun LazyListScope.packSkeletonItems() {
    items(SKELETON_ROWS, key = { "skeleton_$it" }) { index ->
        AccountCellShimmer(
            position = MoonBundlePosition.default(SKELETON_ROWS, index),
            balances = false,
        )
    }
}

@Composable
private fun BatteryHeader(header: HeaderUi?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BatteryIcon(
            level = header?.level ?: 0f,
            style = uikit.R.style.Battery_VeryBig,
            width = 68.dp,
            height = 114.dp,
            emptyState = if (header?.isNegative == true) {
                BatteryView.EmptyState.ACCENT_RED
            } else {
                BatteryView.EmptyState.ACCENT
            },
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
        )
        Text(
            text = stringResource(Localization.battery_refill_title),
            style = UIKit.typography.h2,
            color = UIKit.colorScheme.text.primary,
            textAlign = TextAlign.Center,
        )
        if (header != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (header.isNegative || header.level > 0) {
                    pluralStringResource(Plurals.battery_charges, header.charges.absoluteValue, header.formattedCharges)
                } else {
                    stringResource(header.captionRes)
                },
                style = UIKit.typography.body1,
                color = UIKit.colorScheme.text.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("battery_subtitle"),
            )
            if (header.isNegative) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Localization.battery_refill_negative_balance),
                    style = UIKit.typography.body1,
                    color = UIKit.colorScheme.accent.red,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun BatteryIcon(
    level: Float,
    @StyleRes style: Int,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    emptyState: BatteryView.EmptyState = BatteryView.EmptyState.NONE,
) {
    AndroidView(
        modifier = modifier.size(width, height),
        factory = { context -> BatteryView(ContextThemeWrapper(context, style)) },
        update = { view ->
            view.emptyState = emptyState
            view.setBatteryLevel(level)
        },
    )
}

@Composable
private fun PackCell(pack: PackUi, position: MoonBundlePosition, onClick: () -> Unit) {
    MoonBundleCell(position = position) {
        TextCell(
            modifier = Modifier
                .dimmedUnless(pack.enabled)
                .semantics(mergeDescendants = true) {
                    selected = pack.selected
                    role = Role.RadioButton
                },
            title = pack.title,
            subtitle = pack.caption,
            tags = if (pack.bestValue) {
                { MoonLabel(text = stringResource(Localization.battery_best_value), colors = MoonLabelDefault.blue()) }
            } else {
                null
            },
            image = {
                Box(modifier = Modifier.size(METHOD_ICON_SIZE), contentAlignment = Alignment.Center) {
                    if (!pack.isOther) {
                        BatteryIcon(
                            level = pack.level,
                            style = uikit.R.style.Battery_Big,
                            width = 26.dp,
                            height = METHOD_ICON_SIZE,
                        )
                    }
                }
            },
            content = {
                if (pack.isOther) {
                    MoonChevronRight()
                } else if (pack.selected) {
                    SelectedMark()
                }
            },
            onClick = onClick.takeIf { pack.enabled },
            minHeight = ROW_HEIGHT,
        )
    }
}

@Composable
private fun PayWithCell(method: PayMethodRowUi?, enabled: Boolean, onClick: () -> Unit) {
    MoonBundleCell {
        TextCell(
            modifier = Modifier.dimmedUnless(enabled),
            title = method?.let { stringResource(Localization.battery_pay_with, it.title) }
                ?: stringResource(Localization.battery_pay_with_title),
            subtitle = method?.caption,
            image = method?.let {
                { PayMethodIcon(imageUrl = it.imageUrl, chainBadge = it.chainBadge, isStore = it.kind == PayMethodKind.Store) }
            },
            content = { MoonChevronRight() },
            onClick = onClick.takeIf { enabled },
            minHeight = ROW_HEIGHT,
        )
    }
}

@Composable
internal fun PayMethodIcon(imageUrl: String?, chainBadge: Chain?, isStore: Boolean) {
    val context = LocalContext.current
    if (!isStore) {
        MoonCutBadgedBox(
            badge = chainBadge?.let { chain ->
                { MoonItemImage(image = chain.coin.chainImageResourceUrl(context), size = CHAIN_BADGE_SIZE) }
            },
            direction = BadgeDirection.EndBottom,
        ) {
            MoonItemImage(image = imageUrl, size = METHOD_ICON_SIZE)
        }
    } else {
        MoonCircleIcon(
            painter = painterResource(UIKitIcon.ic_creditcard_28),
            size = METHOD_ICON_SIZE,
            iconSize = 28.dp,
            tint = UIKit.colorScheme.icon.primary,
        )
    }
}

@Composable
private fun LinkCell(
    iconRes: Int,
    title: String,
    subtitle: String,
    position: MoonBundlePosition,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    MoonBundleCell(position = position) {
        TextCell(
            modifier = Modifier.dimmedUnless(enabled),
            title = title,
            subtitle = subtitle,
            image = { MoonItemImage(painter = painterResource(iconRes), size = METHOD_ICON_SIZE) },
            content = { MoonChevronRight() },
            onClick = onClick.takeIf { enabled },
            minHeight = ROW_HEIGHT,
        )
    }
}

@Composable
private fun PromoField(promo: PromoUi, modifier: Modifier, onSubmit: (String) -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val appliedPromo = (promo.state as? PromoState.Applied)?.appliedPromo
    val isLoading = promo.state == PromoState.Loading
    var text by rememberSaveable { mutableStateOf(promo.code ?: appliedPromo.orEmpty()) }
    var hydrated by rememberSaveable { mutableStateOf(text.isNotEmpty()) }
    var wasFocused by remember { mutableStateOf(false) }

    LaunchedEffect(promo.code, appliedPromo) {
        val code = promo.code ?: appliedPromo
        if (!hydrated && code != null && text.isEmpty()) {
            text = code
            hydrated = true
        }
    }

    LaunchedEffect(isFocused) {
        if (!isFocused && wasFocused) {
            if (text.isNotBlank()) {
                if (text != appliedPromo && !isLoading) {
                    onSubmit(text)
                }
            } else if (promo.state != PromoState.Default) {
                onSubmit("")
            }
        }
        wasFocused = isFocused
    }

    MoonTextFieldCell(
        value = text,
        onValueChange = { text = it },
        modifier = modifier,
        hint = stringResource(Localization.promocode_hint),
        singleLine = true,
        isError = promo.state == PromoState.Error,
        loading = isLoading,
        success = appliedPromo != null && text == appliedPromo,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        trailingAction = {
            Text(
                text = stringResource(Localization.paste),
                style = UIKit.typography.label1,
                color = UIKit.colorScheme.accent.blue,
                modifier = Modifier.clickable {
                    text = context.clipboardText()
                    focusManager.clearFocus()
                    onSubmit(text)
                },
            )
        },
        interactionSource = interactionSource,
    )
}

@Composable
private fun Footer(chargingEnabled: Boolean, restoreEnabled: Boolean, onRestoreClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (chargingEnabled) {
                stringResource(Localization.battery_disclaimer)
            } else {
                stringResource(Localization.charging_unavailable)
            },
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.tertiary,
        )
        Text(
            text = stringResource(Localization.restore_purchases),
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.secondary,
            modifier = Modifier
                .dimmedUnless(restoreEnabled)
                .clickable(enabled = restoreEnabled, role = Role.Button, onClick = onRestoreClick),
        )
    }
}

internal fun Modifier.dimmedUnless(enabled: Boolean): Modifier = if (enabled) {
    this
} else {
    alpha(DISABLED_ALPHA).semantics { disabled() }
}

@Composable
internal fun SelectedMark() {
    MoonItemIcon(
        painter = painterResource(UIKitIcon.ic_donemark_thin_28),
        size = 28.dp,
        color = UIKit.colorScheme.accent.blue,
    )
}
