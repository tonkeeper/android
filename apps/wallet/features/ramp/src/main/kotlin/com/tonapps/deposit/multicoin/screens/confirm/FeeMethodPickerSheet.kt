package com.tonapps.deposit.multicoin.screens.confirm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.core.components.assetImageUrl
import com.tonapps.core.components.tokenChainImageUrl
import com.tonapps.deposit.multicoin.screens.confirm.engine.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.engine.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.engine.TxFeeLogic
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonTopAppBar
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.theme.UIKit

data class FeeMethodPickerOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val leading: FeeMethodPickerLeading,
    val enabled: Boolean = true,
    val actionTitle: String? = null,
)

sealed interface FeeMethodPickerLeading {
    data object Battery : FeeMethodPickerLeading
    data class Image(
        val url: String,
        val badgeUrl: String? = null,
    ) : FeeMethodPickerLeading
}

@Composable
fun FeeMethodPickerSheet(
    options: List<FeeMethodPickerOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    onActionSelect: (String) -> Unit = {},
) {
    val router = rememberDialogNavigator { onClose() }
    MoonModalDialog(navigator = router) {
        FeeMethodPickerContent(
            options = options,
            selectedId = selectedId,
            onSelect = { id ->
                onSelect(id)
                router.close()
            },
            onActionSelect = { id ->
                onActionSelect(id)
                router.close()
            },
            onClose = { router.close() },
        )
    }
}

@Composable
fun FeeMethodPickerContent(
    options: List<FeeMethodPickerOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onClose: () -> Unit,
    onActionSelect: (String) -> Unit = {},
) {
    MoonTopAppBar(
        title = stringResource(Localization.network_fee),
        subtitle = stringResource(Localization.choose_method),
        actionIconRes = UIKitIcon.ic_close_16,
        onActionClick = onClose,
        backgroundColor = Color.Transparent,
    )

    MoonBundleCell {
        Column {
            for ((index, option) in options.withIndex()) {
                if (index > 0) {
                    MoonItemDivider()
                }
                FeeMethodRow(
                    option = option,
                    isSelected = option.id == selectedId,
                    onSelect = {
                        if (option.enabled) {
                            onSelect(option.id)
                        } else {
                            onActionSelect(option.id)
                        }
                    },
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
fun TxFee.toPickerOption(
    enabled: Boolean = TxFeeLogic.isSufficient(this),
): FeeMethodPickerOption {
    return FeeMethodPickerOption(
        id = id,
        title = title(),
        subtitle = quotedDetails(),
        leading = when (val account = account) {
            is FeeAccount.Keeper -> FeeMethodPickerLeading.Battery
            is FeeAccount.Chain -> FeeMethodPickerLeading.Image(
                url = account.value.asset.assetImageUrl(),
                badgeUrl = account.value.asset.tokenChainImageUrl(),
            )
        },
        enabled = enabled,
        actionTitle = if (!enabled && account is FeeAccount.Keeper) {
            stringResource(Localization.recharge_battery)
        } else {
            null
        },
    )
}

@Composable
private fun FeeMethodRow(
    option: FeeMethodPickerOption,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val clickable = option.enabled || option.actionTitle != null
    val iconAlpha = if (option.enabled) {
        1f
    } else {
        0.4f
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = clickable, onClick = onSelect)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeeMethodIcon(
            leading = option.leading,
            modifier = Modifier.alpha(iconAlpha),
        )

        Column(modifier = Modifier.weight(1f)) {
            MoonItemTitle(
                text = option.title,
                color = if (option.enabled) {
                    UIKit.colorScheme.text.primary
                } else {
                    UIKit.colorScheme.text.secondary
                },
            )
            Row {
                MoonItemSubtitle(
                    text = option.subtitle,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                )
                if (!option.enabled && option.actionTitle != null) {
                    MoonItemSubtitle(text = " · ")
                    MoonItemSubtitle(
                        text = option.actionTitle,
                        color = UIKit.colorScheme.accent.blue,
                    )
                }
            }
        }

        if (isSelected) {
            MoonItemIcon(
                painter = painterResource(UIKitIcon.ic_done_16),
                color = UIKit.colorScheme.accent.blue,
                size = 16.dp,
            )
        }
    }
}

@Composable
private fun FeeMethodIcon(
    leading: FeeMethodPickerLeading,
    modifier: Modifier = Modifier,
) {
    when (leading) {
        FeeMethodPickerLeading.Battery -> {
            Box(
                modifier = modifier
                    .size(ICON_SIZE)
                    .clip(CircleShape)
                    .background(UIKit.colorScheme.accent.green.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                MoonItemIcon(
                    painter = painterResource(UIKitIcon.ic_flash_24),
                    color = UIKit.colorScheme.accent.green,
                    size = 24.dp,
                )
            }
        }

        is FeeMethodPickerLeading.Image -> {
            MoonCutBadgedBox(
                modifier = modifier,
                badge = leading.badgeUrl?.let { url ->
                    { MoonItemImage(image = url, size = BADGE_SIZE) }
                },
                direction = BadgeDirection.EndBottom,
            ) {
                MoonItemImage(image = leading.url, size = ICON_SIZE)
            }
        }
    }
}

private val ICON_SIZE = 44.dp
private val BADGE_SIZE = 16.dp
