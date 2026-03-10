package com.tonapps.tonkeeper.ui.screen.support

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.TextHeader
import ui.components.button.TKButton
import ui.components.moon.MoonTopAppBar
import ui.theme.ButtonColorsPrimary
import ui.theme.ButtonColorsSecondary
import ui.theme.ButtonSizeLarge
import ui.theme.Dimens
import ui.theme.Shapes
import ui.theme.UIKit

@Composable
fun TelegramIcon(isLightTheme: Boolean) {
    Box(modifier = Modifier.size(72.dp)) {
        Image(
            modifier = Modifier.size(72.dp),
            painter = painterResource(id = R.drawable.ic_telegram),
            contentDescription = null,
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(50.dp)
                .height(32.dp)
                .offset((-34).dp, (-6).dp)
                .border(
                    width = if (isLightTheme) 0.5.dp else 0.dp,
                    color = UIKit.colorScheme.separator.common,
                    shape = Shapes.medium
                )
                .clip(Shapes.medium)
                .background(if (isLightTheme) Color.White else UIKit.colorScheme.background.contentTint),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(UIKit.colorScheme.icon.secondary, CircleShape)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(UIKit.colorScheme.icon.secondary, CircleShape)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(UIKit.colorScheme.icon.secondary, CircleShape)
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(50.dp)
                .height(32.dp)
                .offset(34.dp, 6.dp)
                .border(
                    width = if (isLightTheme) 0.5.dp else 0.dp,
                    color = UIKit.colorScheme.separator.common,
                    shape = Shapes.medium
                )
                .clip(Shapes.medium)
                .background(if (isLightTheme) Color.White else UIKit.colorScheme.background.contentTint),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "???",
                style = TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                ),
                color = UIKit.colorScheme.text.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SupportComposable(
    isLightTheme: Boolean,
    onButtonClick: () -> Unit,
    onCloseClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        MoonTopAppBar(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { onCloseClick() },
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
            TelegramIcon(isLightTheme)
            Spacer(modifier = Modifier.height(20.dp))
            Box(modifier = Modifier.padding(horizontal = Dimens.offsetLarge)) {
                TextHeader(
                    title = stringResource(id = Localization.support_title),
                    description = stringResource(id = Localization.support_subtitle),
                )
            }
            Spacer(modifier = Modifier.height(Dimens.offsetLarge))
            TKButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onButtonClick,
                text = stringResource(id = Localization.ask_question),
                buttonColors = ButtonColorsPrimary,
                size = ButtonSizeLarge,
            )
            Spacer(modifier = Modifier.height(Dimens.offsetMedium))
            TKButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCloseClick,
                text = stringResource(id = Localization.close),
                buttonColors = ButtonColorsSecondary,
                size = ButtonSizeLarge,
            )
            Spacer(modifier = Modifier.height(Dimens.offsetLarge))
        }
    }
}
