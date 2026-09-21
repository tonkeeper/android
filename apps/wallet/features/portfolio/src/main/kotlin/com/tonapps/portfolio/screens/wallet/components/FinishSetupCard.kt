package com.tonapps.portfolio.screens.wallet.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.portfolio.screens.wallet.FinishSetupState
import com.tonapps.portfolio.screens.wallet.FinishSetupLine
import com.tonapps.portfolio.screens.wallet.FinishSetupLineStatus
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import ui.components.moon.MoonChevronRight
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonSwitch
import ui.components.moon.cell.MoonBundleTitleCell
import ui.painterResource
import ui.theme.Shapes
import ui.theme.UIKit

@Composable
internal fun FinishSetupCard(
    state: FinishSetupState,
    pushEnabling: Boolean,
    onBackupClick: () -> Unit,
    onEnablePushClick: () -> Unit,
    onMigrationClick: () -> Unit,
    onEnableBiometryClick: () -> Unit,
    onSkipClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        MoonBundleTitleCell(
            title = stringResource(Localization.setup_finish_title),
            content = if (state.skippable) {
                { SkipButton(onSkipClick = onSkipClick) }
            } else {
                null
            },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(Shapes.medium)
                .background(UIKit.colorScheme.background.content),
        ) {
            state.lines.forEachIndexed { index, line ->
                if (index > 0) {
                    MoonItemDivider()
                }
                when (line) {
                    is FinishSetupLine.Push -> FinishSetupPushRow(
                        enabling = pushEnabling,
                        onEnablePushClick = onEnablePushClick,
                    )

                    is FinishSetupLine.Backup -> FinishSetupBackupRow(
                        status = line.status,
                        onClick = if (line.status == FinishSetupLineStatus.Pending) {
                            onBackupClick
                        } else {
                            null
                        },
                    )

                    is FinishSetupLine.Migration -> FinishSetupMigrationRow(
                        walletsLeft = line.walletsLeft,
                        onClick = onMigrationClick,
                    )

                    is FinishSetupLine.Biometry -> FinishSetupBiometryRow(
                        onClick = onEnableBiometryClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun SkipButton(
    onSkipClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onSkipClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(Localization.setup_finish_skip),
            style = UIKit.typography.label2,
            color = UIKit.colorScheme.text.secondary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            modifier = Modifier,
            painter = painterResource(UIKitIcon.ic_eye_disable_16),
            tint = UIKit.colorScheme.icon.secondary,
            contentDescription = null
        )
    }
}

@Composable
private fun FinishSetupPushRow(
    enabling: Boolean,
    onEnablePushClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEnablePushClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FinishSetupIcon(
            iconRes = UIKitIcon.ic_bell_28,
            color = UIKit.colorScheme.accent.green,
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            text = stringResource(Localization.setup_finish_push),
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.primary,
        )
        MoonSwitch(
            checked = enabling,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun FinishSetupMigrationRow(
    walletsLeft: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FinishSetupIcon(
            iconRes = UIKitIcon.ic_download_28,
            color = UIKit.colorScheme.accent.blue,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(Localization.migration_subtitle),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.primary,
            )
            Text(
                text = pluralStringResource(
                    Plurals.setup_finish_migration_wallets_left,
                    walletsLeft,
                    walletsLeft,
                ),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.secondary,
            )
        }
        MoonChevronRight()
    }
}

@Composable
private fun FinishSetupBiometryRow(
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FinishSetupIcon(
            iconRes = UIKitIcon.ic_faceid_28,
            color = UIKit.colorScheme.accent.green,
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            text = stringResource(Localization.setup_finish_biometry),
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.primary,
        )
        MoonSwitch(
            checked = false,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun FinishSetupBackupRow(
    status: FinishSetupLineStatus,
    onClick: (() -> Unit)?,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            },
        )
        .padding(16.dp)
    val contentAlpha = if (status == FinishSetupLineStatus.Complete) {
        0.72f
    } else {
        1f
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FinishSetupIcon(
            iconRes = UIKitIcon.ic_key_28,
            color = UIKit.colorScheme.accent.orange,
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .alpha(contentAlpha),
            text = stringResource(Localization.setup_finish_backup_wallet),
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.primary,
        )
        when (status) {
            FinishSetupLineStatus.Pending -> MoonChevronRight()

            FinishSetupLineStatus.Complete -> Icon(
                painter = painterResource(UIKitIcon.ic_donemark_28),
                contentDescription = null,
                tint = UIKit.colorScheme.accent.green,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun FinishSetupIcon(
    iconRes: Int,
    color: Color,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(28.dp),
        )
    }
}
