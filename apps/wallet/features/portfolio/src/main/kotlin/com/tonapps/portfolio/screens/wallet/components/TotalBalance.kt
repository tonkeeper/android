package com.tonapps.portfolio.screens.wallet.components

import ui.preview.ThemedPreview
import com.tonapps.chainkit.core.chain.model.num.FiatCurrency
import com.tonapps.chainkit.core.chain.model.num.DisplayUnit
import com.tonapps.chainkit.core.chain.model.num.Decimal
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import androidx.compose.ui.tooling.preview.Preview
import android.view.ContextThemeWrapper
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.chainkit.core.chain.model.num.ScaleSpec
import com.tonapps.core.helper.rememberClipboardManager
import com.tonapps.portfolio.screens.wallet.WalletBattery
import com.tonapps.portfolio.screens.wallet.WalletTotal
import com.tonapps.portfolio.screens.wallet.WalletTotalUi
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.localization.Localization
import ui.animation.BlurCrossfade
import ui.shortAddress
import ui.theme.Shapes
import ui.theme.UIKit
import ui.theme.modifiers.shimmer
import ui.theme.modifiers.textShimmer
import uikit.R
import uikit.widget.BatteryView

private val headerFixedDigitsLimit = BigDecimal.fromInt(1_000_000)
private val headerMinimumFiat = BigDecimal.parseString("0.01")

private val batteryHintRotation = infiniteRepeatable(
    keyframes<Float> {
        durationMillis = 2400
        0f at 600 using EaseOut
        12f at 680 using EaseInOut
        -10f at 780 using EaseInOut
        8f at 880 using EaseInOut
        -6f at 980 using EaseInOut
        3f at 1080 using EaseOut
        0f at 1200
    },
)

private val batteryHintScale = infiniteRepeatable(
    keyframes<Float> {
        durationMillis = 2400
        1f at 600 using EaseOut
        1.12f at 720 using EaseInOut
        1f at 1250
    },
)

private data class HeaderTotalText(val symbol: String, val digits: String)

private fun WalletTotal.toHeaderText(): HeaderTotalText {
    val amount = value.toDisplayValue()
    return when {
        !WalletCurrency.ofOrDefault(currencyCode).isFiat || amount.abs() >= headerFixedDigitsLimit -> {
            HeaderTotalText(currency.symbol, Formatter.formatShort(value = value))
        }
        amount > BigDecimal.ZERO && amount < headerMinimumFiat -> {
            HeaderTotalText(
                Formatter.defaultOptions.lessThanPrefix + currency.symbol,
                formatHeaderDigits(headerMinimumFiat),
            )
        }
        else -> HeaderTotalText(currency.symbol, formatHeaderDigits(amount))
    }
}

private fun formatHeaderDigits(amount: BigDecimal): String {
    return Formatter.formatNumber(
        value = amount,
        spec = ScaleSpec.Fixed(2, RoundingMode.TOWARDS_ZERO),
        trimTrailingZeros = false,
        options = Formatter.defaultOptions,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TotalBalance(
    total: WalletTotalUi,
    onBalanceClick: () -> Unit,
    stale: Boolean = false,
    battery: WalletBattery? = null,
    batteryHint: Boolean = false,
    address: String? = null,
    onBatteryClick: () -> Unit = {},
    onAddressClick: () -> Unit = {},
) {
    val bottomPadding = if (address != null) {
        12.dp
    } else {
        16.dp
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = bottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (total) {
                is WalletTotalUi.HiddenBalance -> HiddenTotalBalance(onClick = onBalanceClick)
                is WalletTotalUi.Amount -> TotalAmount(
                    total = total.total,
                    stale = stale,
                    onClick = onBalanceClick,
                )
            }
            if (battery != null) {
                Battery(
                    modifier = Modifier
                        .semantics { testTagsAsResourceId = true }
                        .testTag("wallet_battery")
                        .clickable(onClick = onBatteryClick),
                    level = battery.level,
                    emptyState = when {
                        battery.negative -> BatteryView.EmptyState.ACCENT_RED
                        battery.viewed -> BatteryView.EmptyState.SECONDARY
                        else -> BatteryView.EmptyState.ACCENT
                    },
                    hint = batteryHint,
                )
            }
        }
        if (address != null) {
            WalletAddress(address = address, onClick = onAddressClick)
        }
    }
}

@Composable
private fun TotalAmount(
    total: WalletTotal,
    stale: Boolean,
    onClick: () -> Unit,
) {
    val text = remember(total) { total.toHeaderText() }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .textShimmer(isActive = stale),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text.symbol,
                style = UIKit.typography.h1,
                fontSize = 44.sp,
                fontWeight = FontWeight.Medium,
            )
            BlurCrossfade(targetState = text.digits) { formatted ->
                Text(
                    text = formatted,
                    style = UIKit.typography.h1,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun HiddenTotalBalance(onClick: () -> Unit) {
    val amountStyle = UIKit.typography.h1.copy(fontSize = 44.sp, fontWeight = FontWeight.Medium)
    val textMeasurer = rememberTextMeasurer()
    val amountHeight = with(LocalDensity.current) {
        remember(textMeasurer, amountStyle) {
            textMeasurer.measure(text = HIDDEN_BALANCE, style = amountStyle).size.height
        }.toDp()
    }

    Box(
        modifier = Modifier.height(amountHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 76.dp, height = 40.dp)
                .clip(CircleShape)
                .background(UIKit.colorScheme.buttonSecondary.primaryBackground)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                // The asterisk glyph sits at the top of the line box; the shift centers it in the capsule.
                modifier = Modifier.offset(y = 5.dp),
                text = HIDDEN_BALANCE,
                style = UIKit.typography.num2,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WalletAddress(
    address: String,
    onClick: () -> Unit,
) {
    val clipboard = rememberClipboardManager()

    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(Shapes.medium)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = stringResource(Localization.receiving_address),
                onLongClickLabel = stringResource(Localization.copy),
                onLongClick = { clipboard.copy(address) },
                hapticFeedbackEnabled = false,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = address.shortAddress,
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.secondary,
        )
        Icon(
            painter = painterResource(UIKitIcon.ic_switch_16),
            tint = UIKit.colorScheme.icon.secondary,
            contentDescription = null,
        )
    }
}

@Composable
internal fun TotalBalance(shimmerPhase: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.width(120.dp).shimmer(shimmerPhase),
            text = "",
            style = UIKit.typography.h1,
            fontSize = 44.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Battery(
    modifier: Modifier = Modifier,
    level: Float,
    emptyState: BatteryView.EmptyState = BatteryView.EmptyState.NONE,
    hint: Boolean = false,
) {
    val hintModifier = if (hint) {
        rememberBatteryHintModifier()
    } else {
        Modifier
    }
    // The interop view does not expose Compose semantics, so the box carries the test tag and the click.
    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.size(20.dp, 34.dp).then(hintModifier),
            factory = { context ->
                val themed = ContextThemeWrapper(context, R.style.Battery_Medium)
                BatteryView(themed)
            },
            update = { view ->
                view.emptyState = emptyState
                view.setBatteryLevel(level)
            },
        )
    }
}

@Composable
private fun rememberBatteryHintModifier(): Modifier {
    val transition = rememberInfiniteTransition(label = "battery_hint")
    val rotation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = batteryHintRotation,
        label = "battery_hint_rotation",
    )
    val scale = transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = batteryHintScale,
        label = "battery_hint_scale",
    )
    return Modifier.graphicsLayer {
        rotationZ = rotation.value
        scaleX = scale.value
        scaleY = scale.value
    }
}

@Preview
@Composable
private fun TotalBalancePreview() {
    ThemedPreview(isDarkOnly = true) {
        TotalBalance(
            total = WalletTotalUi.Amount(
                WalletTotal(
                    walletId = "preview",
                    currencyCode = "USD",
                    currency = FiatCurrency("$"),
                    value = DisplayUnit(BigDecimal.parseString("62.2"), Decimal(2)),
                    perps = null,
                ),
            ),
            onBalanceClick = {},
            address = "UQAn4f2k9xVbq0D8s7L1mT3cR6yWzN5pE80D-",
        )
    }
}

@Preview
@Composable
private fun TotalBalanceHiddenPreview() {
    ThemedPreview(isDarkOnly = true) {
        TotalBalance(
            total = WalletTotalUi.HiddenBalance,
            onBalanceClick = {},
            address = "UQAn4f2k9xVbq0D8s7L1mT3cR6yWzN5pE80D-",
        )
    }
}

@Preview
@Composable
private fun TotalBalanceDustPreview() {
    ThemedPreview(isDarkOnly = true) {
        TotalBalance(
            total = WalletTotalUi.Amount(
                WalletTotal(
                    walletId = "preview",
                    currencyCode = "USD",
                    currency = FiatCurrency("$"),
                    value = DisplayUnit(BigDecimal.parseString("0.004"), Decimal(2)),
                    perps = null,
                ),
            ),
            onBalanceClick = {},
            address = "UQAn4f2k9xVbq0D8s7L1mT3cR6yWzN5pE80D-",
        )
    }
}

@Preview
@Composable
private fun TotalBalanceBatteryHintPreview() {
    ThemedPreview(isDarkOnly = true) {
        TotalBalance(
            total = WalletTotalUi.Amount(
                WalletTotal(
                    walletId = "preview",
                    currencyCode = "USD",
                    currency = FiatCurrency("$"),
                    value = DisplayUnit(BigDecimal.parseString("7345"), Decimal(2)),
                    perps = null,
                ),
            ),
            onBalanceClick = {},
            battery = WalletBattery(walletId = "preview", level = 0f, viewed = false, negative = false, knownEmpty = true),
            batteryHint = true,
            address = "UQAn4f2k9xVbq0D8s7L1mT3cR6yWzN5pE80D-",
        )
    }
}
