package com.tonapps.perps.screens.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonActionIcon
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.Dimens
import ui.theme.UIKit

@Composable
internal fun PerpsFundingTopBar(
    title: String,
    onClose: () -> Unit,
    subtitle: String? = null,
    closeIdentifier: String = "perps_funding_close",
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.heightBar)
            .padding(horizontal = Dimens.offsetMedium),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Dimens.sizeAction + Dimens.offsetExtraSmall,
                vertical = 18.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = UIKit.colorScheme.text.primary,
                style = UIKit.typography.h3,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = UIKit.colorScheme.text.secondary,
                    style = UIKit.typography.body2,
                    textAlign = TextAlign.Center,
                )
            }
        }
        MoonActionIcon(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = Dimens.offsetMedium)
                .semantics {
                    testTagsAsResourceId = true
                    testTag = closeIdentifier
                },
            painter = painterResource(UIKitIcon.ic_close_16),
            onClick = onClose,
            tintColor = UIKit.colorScheme.buttonSecondary.primaryForeground,
            contentDescription = "Action",
        )
    }
}

@Preview(widthDp = 360, locale = "bg")
@Composable
private fun PerpsFundingTopBarPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsFundingTopBar(
            title = stringResource(Localization.perps_deposit_title),
            onClose = {},
        )
        PerpsFundingTopBar(
            title = stringResource(Localization.perps_withdraw_title),
            onClose = {},
        )
        PerpsFundingTopBar(
            title = stringResource(Localization.choose_asset),
            onClose = {},
            subtitle = stringResource(Localization.perps_deposit_converted_note),
        )
    }
}
