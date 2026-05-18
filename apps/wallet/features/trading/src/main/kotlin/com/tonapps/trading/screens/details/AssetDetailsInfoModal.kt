package com.tonapps.trading.screens.details

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.preview.ThemedPreview
import ui.theme.UIKit

internal enum class AssetInfoModalType {
    Stock,
    Etf,
}

private data class AssetInfoContent(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val firstBulletRes: Int,
    @StringRes val secondBulletRes: Int,
    @StringRes val thirdBulletRes: Int,
)

@Composable
internal fun AssetDetailsInfoModal(
    type: AssetInfoModalType,
    onDismiss: () -> Unit,
) {
    val navigator = rememberDialogNavigator(onClose = onDismiss)

    val content = type.content()

    MoonModalDialog(navigator = navigator) {
        MoonTopAppBarSimple(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { navigator.close() },
            backgroundColor = Color.Transparent,
        )
        MoonTextContentCell(
            modifier = Modifier.padding(horizontal = 16.dp),
            title = stringResource(content.titleRes),
            description = stringResource(content.descriptionRes),
            descriptionStyle = UIKit.typography.body2,
        )
        Spacer(modifier = Modifier.height(32.dp))
        MoonBundleCell {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Bullet(stringResource(content.firstBulletRes))
                Bullet(stringResource(content.secondBulletRes))
                Bullet(stringResource(content.thirdBulletRes))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        MoonAccentButton(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            text = stringResource(Localization.ok),
            size = ButtonSizeLarge,
            onClick = { navigator.close() },
        )
    }
}

@Composable
private fun Bullet(
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

private fun AssetInfoModalType.content(): AssetInfoContent = when (this) {
    AssetInfoModalType.Stock -> AssetInfoContent(
        titleRes = Localization.asset_info_modal_stock_title,
        descriptionRes = Localization.asset_info_modal_stock_description,
        firstBulletRes = Localization.asset_info_modal_stock_bullet_1,
        secondBulletRes = Localization.asset_info_modal_stock_bullet_2,
        thirdBulletRes = Localization.asset_info_modal_stock_bullet_3,
    )

    AssetInfoModalType.Etf -> AssetInfoContent(
        titleRes = Localization.asset_info_modal_etf_title,
        descriptionRes = Localization.asset_info_modal_etf_description,
        firstBulletRes = Localization.asset_info_modal_etf_bullet_1,
        secondBulletRes = Localization.asset_info_modal_etf_bullet_2,
        thirdBulletRes = Localization.asset_info_modal_etf_bullet_3,
    )
}

@Preview
@Composable
private fun AssetDetailsInfoModalStockPreview() {
    ThemedPreview {
        AssetDetailsInfoModal(
            type = AssetInfoModalType.Stock,
            onDismiss = {}
        )
    }
}

@Preview
@Composable
private fun AssetDetailsInfoModalEtfPreview() {
    ThemedPreview {
        AssetDetailsInfoModal(
            type = AssetInfoModalType.Etf,
            onDismiss = {}
        )
    }
}
