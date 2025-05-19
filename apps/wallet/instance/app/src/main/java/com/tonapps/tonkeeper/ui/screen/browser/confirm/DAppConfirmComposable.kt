package com.tonapps.tonkeeper.ui.screen.browser.confirm

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import uikit.compose.AppTheme
import uikit.compose.Dimens
import uikit.compose.UIKit
import uikit.compose.components.AsyncImage
import uikit.compose.components.Checkbox
import uikit.compose.components.Header
import uikit.compose.components.PrimaryButton
import uikit.compose.components.TextHeader

@Composable
fun Disclaimer() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(UIKit.colors.fieldBackground)
            .padding(Dimens.offsetMedium)
    ) {
        Text(
            modifier = Modifier.padding(end = Dimens.offsetLarge),
            text = stringResource(id = Localization.dapp_disclaimer),
            style = UIKit.typography.body2,
            color = UIKit.colors.textPrimary,
        )
        Icon(
            painter = painterResource(id = UIKitIcon.ic_exclamationmark_circle_16),
            contentDescription = null,
            tint = UIKit.colors.iconSecondary,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

@Composable
fun DAppIcon(
    icon: Uri?
) {
    Box(modifier = Modifier.padding(Dimens.offsetMedium)) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(UIKit.colors.backgroundContent),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                AsyncImage(
                    model = icon,
                    modifier = Modifier.size(96.dp)
                )
            } else {
                Image(
                    painter = painterResource(id = UIKitIcon.ic_globe_56),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(UIKit.colors.iconSecondary)
                )
            }
        }
    }
}

@Composable
fun DAppConfirmComposable(
    host: String,
    icon: Uri? = null,
    name: String,
    onOpen: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onFinishClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Header(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { onFinishClick() },
            ignoreSystemOffset = true,
            showDivider = false,
            backgroundColor = Color.Transparent
        )

        Column(
            modifier = Modifier
                .padding(horizontal = Dimens.offsetMedium)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // DAppIcon(icon = icon)
            Spacer(modifier = Modifier.height(Dimens.offsetMedium))
            Box(modifier = Modifier.padding(horizontal = Dimens.offsetMedium)) {
                TextHeader(
                    title = name,
                    description = host,
                )
            }
            Spacer(modifier = Modifier.height(Dimens.offsetLarge))
            Disclaimer()
            Spacer(modifier = Modifier.height(Dimens.offsetMedium))
            PrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpen,
                text = stringResource(id = Localization.open)
            )
            Spacer(modifier = Modifier.height(Dimens.offsetMedium + 12.dp))

            var isChecked by remember { mutableStateOf(false) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = {
                        isChecked = it
                        onCheckedChange(it)
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    modifier = Modifier.clickable { isChecked = !isChecked },
                    text = stringResource(id = Localization.do_not_show_again),
                    style = UIKit.typography.body1,
                    color = UIKit.colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.height(Dimens.offsetLarge))
        }
    }
}

@Preview
@Composable
private fun DAppConfirmComposablePreview() {
    UIKit(theme = AppTheme.BLUE) {
        DAppConfirmComposable(
            host = "app.ston.fi",
            name = "Demo DApp",
            onOpen = {},
            onCheckedChange = {},
            onFinishClick = {},
        )
    }
}