package com.tonapps.portfolio.screens.wallet.components

import android.view.ContextThemeWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.core.helper.rememberClipboardManager
import com.tonapps.portfolio.screens.wallet.WalletBattery
import com.tonapps.portfolio.screens.wallet.WalletTotal
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.shortAddress
import ui.theme.Shapes
import ui.theme.UIKit
import ui.theme.modifiers.shimmer
import uikit.HapticHelper
import uikit.R
import uikit.widget.BatteryView

@Composable
internal fun TotalBalance(
    total: WalletTotal,
    battery: WalletBattery? = null,
    address: String? = null,
    onBatteryClick: () -> Unit = {},
    onAddressClick: () -> Unit = {},
) {
    val totalFormatted = remember(total.value) {
        Formatter.formatShort(value = total.value)
    }
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
            Text(
                text = total.currency.symbol,
                style = UIKit.typography.h1,
                fontSize = 44.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = totalFormatted,
                style = UIKit.typography.h1,
                fontSize = 44.sp,
                fontWeight = FontWeight.Medium,
            )
            battery?.let {
                Battery(
                    modifier = Modifier.clickable(onClick = onBatteryClick),
                    level = it.level,
                    emptyState = when {
                        it.negative -> BatteryView.EmptyState.ACCENT_RED
                        it.viewed -> BatteryView.EmptyState.SECONDARY
                        else -> BatteryView.EmptyState.ACCENT
                    },
                )
            }
        }
        address?.let {
            WalletAddress(address = it, onClick = onAddressClick)
        }
    }
}

@Composable
private fun WalletAddress(
    address: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = rememberClipboardManager()

    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(Shapes.medium)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = stringResource(Localization.receiving_address),
                onLongClickLabel = stringResource(Localization.copy),
                onLongClick = {
                    clipboard.copy(address)
                    HapticHelper.success(context)
                },
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
