package com.tonapps.portfolio.screens.wallet.components

import ui.preview.ThemedPreview
import com.tonapps.chainkit.core.chain.model.num.FiatCurrency
import com.tonapps.chainkit.core.chain.model.num.DisplayUnit
import com.tonapps.chainkit.core.chain.model.num.Decimal
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import androidx.compose.ui.tooling.preview.Preview
import android.view.ContextThemeWrapper
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tonapps.chainkit.core.chain.model.num.Formatter
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

@Composable
internal fun TotalBalance(
    total: WalletTotalUi,
    onBalanceClick: () -> Unit,
    stale: Boolean = false,
    battery: WalletBattery? = null,
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
                    modifier = Modifier.clickable(onClick = onBatteryClick),
                    level = battery.level,
                    emptyState = when {
                        battery.negative -> BatteryView.EmptyState.ACCENT_RED
                        battery.viewed -> BatteryView.EmptyState.SECONDARY
                        else -> BatteryView.EmptyState.ACCENT
                    },
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
    val totalFormatted = remember(total.value) {
        Formatter.formatShort(value = total.value)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .textShimmer(isActive = stale),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = total.currency.symbol,
                style = UIKit.typography.h1,
                fontSize = 44.sp,
                fontWeight = FontWeight.Medium,
            )
            BlurCrossfade(targetState = totalFormatted) { formatted ->
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
) {
    AndroidView(
        modifier = Modifier
            .size(20.dp, 34.dp)
            .then(modifier),
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
                    value = DisplayUnit(BigDecimal.parseString("45.31"), Decimal(2)),
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

