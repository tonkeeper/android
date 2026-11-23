package com.tonapps.perps.region

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.dialog.MoonModalDialog
import ui.components.moon.dialog.rememberDialogNavigator
import ui.preview.ThemedPreview
import ui.theme.UIKit

@Composable
internal fun PerpsRegionRestrictedBanner(
    onLearnMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color = UIKit.colorScheme.accent.orange, shape = UIKit.shapes.large)
            .padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 13.dp)
            .testTag("perps_region_restricted"),
    ) {
        Text(
            text = stringResource(Localization.perps_region_restricted_title),
            style = UIKit.typography.label1.copy(lineHeight = 24.sp, lineHeightStyle = UNTRIMMED_LINE_HEIGHT),
            color = Color.Black,
        )
        Text(
            text = stringResource(Localization.perps_region_restricted_banner),
            style = UIKit.typography.body2.copy(lineHeightStyle = UNTRIMMED_LINE_HEIGHT),
            color = Color.Black.copy(alpha = 0.76f),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier
                .clickable(onClick = onLearnMore)
                .testTag("perps_region_restricted_learn_more"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Localization.learn_more),
                style = UIKit.typography.label2.copy(lineHeight = 20.sp, lineHeightStyle = UNTRIMMED_LINE_HEIGHT),
                color = Color.Black,
            )
            Spacer(modifier = Modifier.width(2.dp))
            MoonItemIcon(
                painter = painterResource(UIKitIcon.ic_chevron_right_12),
                size = 12.dp,
                color = Color.Black,
            )
        }
    }
}

@Composable
internal fun PerpsRegionRestrictedSheet(onDismiss: () -> Unit) {
    val navigator = rememberDialogNavigator(onClose = onDismiss)

    MoonModalDialog(navigator = navigator) {
        MoonTopAppBarSimple(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { navigator.close() },
            backgroundColor = Color.Transparent,
            actionModifier = Modifier.semantics {
                testTagsAsResourceId = true
                testTag = "bottom_sheet_close"
            },
        )
        Image(
            painter = painterResource(UIKitIcon.ic_globe_84),
            contentDescription = null,
            colorFilter = ColorFilter.tint(UIKit.colorScheme.icon.primary),
            modifier = Modifier.size(84.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        MoonTextContentCell(
            modifier = Modifier.padding(horizontal = 16.dp),
            title = stringResource(Localization.perps_region_restricted_title),
            titleStyle = UIKit.typography.h2,
            description = stringResource(Localization.perps_region_restricted_caption),
        )
        Spacer(modifier = Modifier.height(16.dp))
        MoonAccentButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .semantics {
                    testTagsAsResourceId = true
                    testTag = "perps_region_restricted_got_it"
                },
            text = stringResource(Localization.got_it),
            size = ButtonSizeLarge,
            onClick = { navigator.close() },
        )
    }
}

private val UNTRIMMED_LINE_HEIGHT = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

@Preview
@Composable
private fun PerpsRegionRestrictedBannerPreview() {
    ThemedPreview {
        PerpsRegionRestrictedBanner(
            onLearnMore = {},
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Preview
@Composable
private fun PerpsRegionRestrictedSheetPreview() {
    ThemedPreview {
        PerpsRegionRestrictedSheet(onDismiss = {})
    }
}
