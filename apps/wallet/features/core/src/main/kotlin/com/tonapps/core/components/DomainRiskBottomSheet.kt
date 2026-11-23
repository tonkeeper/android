package com.tonapps.core.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.blockchain.model.DappConnectRequest.DomainStatus
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.components.moon.tintedButtonColors
import ui.preview.ThemedPreview
import ui.theme.UIKit

@Composable
fun MoonDomainRiskBottomSheet(
    status: DomainStatus?,
    confirmText: String,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    val router = rememberDialogNavigator { onClose() }
    MoonModalDialog(navigator = router) {
        DomainRiskContent(
            status = status,
            confirmText = confirmText,
            onConfirm = {
                onConfirm()
                router.close()
            },
            onClose = { router.close() },
        )
    }
}

@Composable
private fun ColumnScope.DomainRiskContent(
    status: DomainStatus?,
    confirmText: String,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    val isCritical = status == DomainStatus.Scam || status == DomainStatus.Invalid
    MoonTopAppBarSimple(
        title = "",
        actionIconRes = UIKitIcon.ic_close_16,
        onActionClick = onClose,
        backgroundColor = Color.Transparent,
    )

    Spacer(modifier = Modifier.height(8.dp))

    MoonItemImage(
        painter = painterResource(
            if (isCritical) {
                UIKitIcon.ic_exclamationmark_triangle_colorful_84
            } else {
                UIKitIcon.ic_exclamationmark_circle_colorful_84
            }
        ),
        size = 84.dp,
        shape = RectangleShape,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = when (status) {
            DomainStatus.Scam -> stringResource(Localization.domain_risk_scam_title)
            DomainStatus.Invalid -> stringResource(Localization.domain_risk_invalid_title)
            DomainStatus.Valid,
            DomainStatus.Unknown,
            null -> stringResource(Localization.domain_risk_unknown_title)
        },
        modifier = Modifier.padding(horizontal = 32.dp),
        style = UIKit.typography.h2,
        color = UIKit.colorScheme.text.primary,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = when (status) {
            DomainStatus.Scam -> stringResource(Localization.domain_risk_scam_description)
            DomainStatus.Invalid -> stringResource(Localization.domain_risk_invalid_description)
            DomainStatus.Valid,
            DomainStatus.Unknown,
            null -> stringResource(Localization.domain_status_unknown)
        },
        modifier = Modifier.padding(horizontal = 32.dp),
        style = UIKit.typography.body1,
        color = UIKit.colorScheme.text.secondary,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(32.dp))

    val confirmAccent = if (isCritical) {
        UIKit.colorScheme.accent.red
    } else {
        UIKit.colorScheme.accent.orange
    }
    val confirmColors = tintedButtonColors(confirmAccent)

    MoonAccentButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = onConfirm,
        text = confirmText,
        buttonColors = confirmColors,
        size = ButtonSizeLarge,
    )

    Spacer(modifier = Modifier.height(16.dp))

    MoonAccentButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = onClose,
        text = stringResource(id = Localization.cancel),
        buttonColors = ButtonColorsSecondary,
        size = ButtonSizeLarge,
    )

    Spacer(modifier = Modifier.height(16.dp))
}

@Preview
@Composable
private fun DomainRiskInvalidPreview() {
    ThemedPreview {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DomainRiskContent(
                status = DomainStatus.Invalid,
                confirmText = stringResource(Localization.confirm_anyway),
                onConfirm = {},
                onClose = {},
            )
        }
    }
}
