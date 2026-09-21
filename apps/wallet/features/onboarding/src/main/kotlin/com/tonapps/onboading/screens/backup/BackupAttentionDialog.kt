package com.tonapps.onboading.screens.backup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonCheckbox
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBottomButtonCell
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.preview.ThemedPreview
import ui.theme.Dimens
import ui.theme.UIKit

@Composable
fun BackupAttentionDialog(
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onClose)
    var isChecked by remember { mutableStateOf(false) }

    MoonModalDialog(navigator = navigator) {
        MoonTopAppBarSimple(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { navigator.close() },
            backgroundColor = Color.Transparent,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(UIKitIcon.ic_exclamationmark_circle_colorful_84),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(84.dp),
            )

            Spacer(modifier = Modifier.height(Dimens.offsetMedium))

            Text(
                text = stringResource(Localization.backup_attention_title),
                style = UIKit.typography.h2,
                color = UIKit.colorScheme.text.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Dimens.offsetMedium),
            )

            Spacer(modifier = Modifier.height(Dimens.offsetMedium))

            MoonBundleCell {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BackupAttentionBullet(
                        text = stringResource(Localization.backup_attention_line_1),
                    )
                    BackupAttentionBullet(
                        text = stringResource(Localization.backup_attention_line_2),
                    )
                    BackupAttentionBullet(
                        text = stringResource(Localization.backup_attention_line_3),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            MoonBundleCell {
                BackupAttentionCheckbox(
                    isChecked = isChecked,
                    onCheckedChanged = { isChecked = it },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            MoonBottomButtonCell(
                text = stringResource(Localization.backup_attention_confirm),
                enabled = isChecked,
                onClick = {
                    onConfirm()
                    navigator.close()
                },
            )
        }
    }
}

@Composable
private fun BackupAttentionBullet(
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp),
            text = "•",
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

@Composable
private fun BackupAttentionCheckbox(
    isChecked: Boolean,
    onCheckedChanged: (Boolean) -> Unit,
) {
    val color = UIKit.colorScheme.accent.orange
    val prefix = stringResource(Localization.backup_attention_checkbox_prefix)
    val highlight = stringResource(Localization.backup_attention_checkbox_highlight)
    val suffix = stringResource(Localization.backup_attention_checkbox_suffix)
    val text = remember(prefix, highlight, suffix, color) {
        buildAnnotatedString {
            append(prefix)
            withStyle(SpanStyle(color = color)) {
                append(highlight)
            }
            append(suffix)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {
                onCheckedChanged(!isChecked)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MoonCheckbox(checked = isChecked)
        Text(
            text = text,
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.primary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview
@Composable
private fun BackupAttentionDialogPreview() {
    ThemedPreview {
        BackupAttentionDialog(
            onConfirm = {},
            onClose = {},
        )
    }
}
