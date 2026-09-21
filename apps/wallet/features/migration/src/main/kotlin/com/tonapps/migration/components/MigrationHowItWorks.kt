package com.tonapps.migration.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.ButtonColorsPrimary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.painterResource
import ui.theme.UIKit

@Composable
internal fun MigrationHowItWorksLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        MoonItemIcon(
            painter = painterResource(UIKitIcon.ic_information_circle_12),
            color = UIKit.colorScheme.accent.blue,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(Localization.migration_how_it_works),
            color = UIKit.colorScheme.accent.blue,
            style = UIKit.typography.body2,
        )
    }
}

@Composable
internal fun MigrationHowItWorksDialog(
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator { onClose() }

    MoonModalDialog(navigator = navigator) {
        MoonTopAppBarSimple(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { navigator.close() },
            backgroundColor = Color.Transparent,
        )

        MoonTextContentCell(
            title = stringResource(Localization.migration_how_it_works_title),
            description = stringResource(Localization.migration_how_it_works_intro),
            titleStyle = UIKit.typography.h2,
            descriptionStyle = UIKit.typography.body2,
        )

        Spacer(Modifier.height(32.dp))

        MoonBundleCell {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                MigrationInfoBullet(stringResource(Localization.migration_how_it_works_will_migrate))
                MigrationInfoBullet(stringResource(Localization.migration_how_it_works_will_stay))
                MigrationInfoBullet(stringResource(Localization.migration_how_it_works_note))
            }
        }

        Spacer(Modifier.height(16.dp))

        MoonAccentButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            text = stringResource(Localization.ok),
            size = ButtonSizeLarge,
            buttonColors = ButtonColorsPrimary,
            onClick = { navigator.close() },
        )
    }
}

@Composable
private fun MigrationInfoBullet(
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            modifier = Modifier.padding(start = 4.dp),
            text = "\u2022",
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.primary,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = text,
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.primary,
        )
    }
}
