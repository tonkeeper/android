package com.tonapps.tonkeeper.ui.screen.browser.share

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import uikit.compose.AppTheme
import uikit.compose.Dimens
import uikit.compose.UIKit
import uikit.compose.components.AsyncImage
import uikit.compose.components.Header
import uikit.compose.components.PrimaryButton
import uikit.compose.components.SecondaryButton
import uikit.compose.components.TextHeader

@Composable
fun UrlView(url: Uri) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(UIKit.colors.fieldBackground)
            .padding(horizontal = Dimens.offsetMedium),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = url.toString().removePrefix("${url.scheme}://"),
            style = UIKit.typography.body1,
            maxLines = 1,
            color = UIKit.colors.textPrimary,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(56.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, UIKit.colors.fieldBackground)
                    )
                )
                .align(Alignment.CenterEnd)
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
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(32.dp)
                .offset(6.dp, 6.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(UIKit.colors.accentBlue),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = UIKitIcon.ic_link_outline_28),
                contentDescription = null,
                colorFilter = ColorFilter.tint(UIKit.colors.iconPrimary)
            )
        }
    }
}

@Composable
fun DAppShareComposable(
    url: Uri,
    icon: Uri? = null,
    name: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
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
            DAppIcon(icon = icon)
            Spacer(modifier = Modifier.height(Dimens.offsetMedium))
            Box(modifier = Modifier.padding(horizontal = Dimens.offsetMedium)) {
                TextHeader(
                    title = stringResource(id = Localization.dapp_share_title, name),
                    description = stringResource(id = Localization.dapp_share_subtitle, name),
                )
            }
            Spacer(modifier = Modifier.height(Dimens.offsetLarge))
            UrlView(url = url)
            Spacer(modifier = Modifier.height(Dimens.offsetMedium))
            PrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCopy,
                text = stringResource(id = Localization.copy_link)
            )
            Spacer(modifier = Modifier.height(Dimens.offsetMedium))
            SecondaryButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onShare,
                text = stringResource(id = Localization.share)
            )
            Spacer(modifier = Modifier.height(Dimens.offsetLarge))
        }
    }
}

@Preview
@Composable
private fun DAppShareComposablePreview() {
    val wallet = WalletEntity.EMPTY
    UIKit(theme = AppTheme.BLUE) {
        DAppShareComposable(
            url = "https://app.tonkeeper.com/https%3A%2F%2Fapp.ston.fi%2Fswap%3FchartVisible%3Dfalse%26ft%3DTON%26tt%3DSTON".toUri(),
            name = "Demo DApp",
            onCopy = {},
            onShare = {},
            onFinishClick = {},
        )
    }
}