package com.tonapps.wallet.features.ledger.progress

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.features.ledger.common.LedgerDevicePicture
import ui.components.moon.MoonLoader
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit

@Composable
internal fun LedgerStepsCard(
    connecting: Boolean,
    isUsb: Boolean,
    displayText: String,
    steps: List<LedgerStepItem>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(UIKit.colorScheme.background.content, UIKit.shapes.large)
            .clipToBounds(),
    ) {
        LedgerStepsDevice(
            bluetooth = connecting && !isUsb,
            displayText = displayText,
            modifier = Modifier.padding(start = 40.dp, top = 40.dp, bottom = 16.dp),
        )
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)) {
            steps.forEach { LedgerStepRow(it) }
        }
    }
}

@Composable
private fun LedgerStepsDevice(bluetooth: Boolean, displayText: String, modifier: Modifier = Modifier) {
    val bluetoothAlpha by animateFloatAsState(
        targetValue = if (bluetooth) { 1f } else { 0f },
        animationSpec = if (bluetooth) { tween(FADE_MILLIS, delayMillis = SHIFT_DELAY_MILLIS) } else { tween(BLUETOOTH_HIDE_MILLIS) },
        label = "bluetooth",
    )
    val shift by animateDpAsState(
        targetValue = if (bluetooth) { 24.dp } else { (-42).dp },
        animationSpec = tween(SHIFT_MILLIS, delayMillis = if (bluetooth) { SHIFT_DELAY_MILLIS } else { 0 }),
        label = "shift",
    )
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            modifier = Modifier
                .zIndex(1f)
                .size(width = 42.dp, height = 56.dp)
                .alpha(bluetoothAlpha),
            painter = painterResource(UIKitIcon.img_ledger_bluetooth),
            contentDescription = null,
        )
        LedgerDevicePicture(
            modifier = Modifier
                .offset(x = shift)
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .size(width = 353.dp, height = 56.dp),
            displayText = displayText,
        )
    }
}

@Composable
private fun LedgerStepRow(item: LedgerStepItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Box(modifier = Modifier.size(width = 16.dp, height = 20.dp), contentAlignment = Alignment.Center) {
            when (item.status) {
                LedgerStepStatus.Pending -> Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(UIKit.colorScheme.icon.tertiary, CircleShape),
                )
                LedgerStepStatus.Current -> MoonLoader(
                    modifier = Modifier.size(16.dp),
                    color = UIKit.colorScheme.icon.tertiary,
                )
                LedgerStepStatus.Done -> Icon(
                    modifier = Modifier.size(16.dp),
                    painter = painterResource(UIKitIcon.ic_done_16),
                    tint = UIKit.colorScheme.accent.green,
                    contentDescription = null,
                )
                LedgerStepStatus.Failed -> Icon(
                    modifier = Modifier.size(16.dp),
                    painter = painterResource(UIKitIcon.ic_exclamationmark_circle_16),
                    tint = UIKit.colorScheme.accent.red,
                    contentDescription = null,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = item.label,
                style = UIKit.typography.body2,
                color = if (item.status == LedgerStepStatus.Done) { UIKit.colorScheme.accent.green } else { UIKit.colorScheme.text.primary },
            )
            if (item.hint != null) {
                Text(
                    text = item.hint,
                    style = UIKit.typography.body2,
                    color = if (item.status == LedgerStepStatus.Failed) { UIKit.colorScheme.accent.red } else { UIKit.colorScheme.text.secondary },
                )
            }
        }
    }
}

@Preview
@Composable
private fun LedgerStepsCardPreview() {
    ThemedPreview {
        LedgerStepsCard(
            connecting = false,
            isUsb = false,
            displayText = "TON",
            steps = listOf(
                LedgerStepItem("Connect Ledger to your device via Bluetooth", LedgerStepStatus.Done, null),
                LedgerStepItem("Unlock it and open the TON app", LedgerStepStatus.Done, null),
                LedgerStepItem("Confirm the transaction on your Ledger", LedgerStepStatus.Current, null),
            ),
        )
    }
}

private const val FADE_MILLIS = 300
private const val BLUETOOTH_HIDE_MILLIS = 120
private const val SHIFT_MILLIS = 350
private const val SHIFT_DELAY_MILLIS = 200
