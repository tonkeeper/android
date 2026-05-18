package com.tonapps.dapp.warning

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.core.net.toUri
import com.tonapps.mvi.props.observeSafeState
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonAsyncImage
import ui.components.moon.MoonTopAppBar
import ui.components.moon.cell.MoonButtonCell
import ui.components.moon.cell.MoonInfoCell
import ui.components.moon.cell.MoonTextCheckboxCell
import ui.components.moon.cell.MoonTextContentCell
import ui.preview.ThemedPreview
import ui.theme.Dimens
import ui.theme.UIKit

@Preview
@Composable
private fun DAppConfirmScreenPreview() {
    ThemedPreview {
        Content(
            data = DAppConfirmState(
                host = "https://uniswap.com",
                name = "Uniswap"
            ),
            onOpen = {},
            onFinishClick = {},
            onChecked = {}
        )
    }
}

@Composable
fun DAppConfirmScreen(
    viewModel: DAppConfirmFeature,
    onOpen: () -> Unit,
    onFinishClick: () -> Unit,
) {
    val global by viewModel.state.global.observeSafeState()
    Content(
        data = global,
        onOpen = onOpen,
        onFinishClick = onFinishClick,
        onChecked = { isChecked -> viewModel.sendAction(DAppConfirmAction.UpdateCheckbox(isChecked)) }
    )
}

@Composable
private fun Content(
    data: DAppConfirmState,
    onOpen: () -> Unit,
    onFinishClick: () -> Unit,
    onChecked: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        MoonTopAppBar(
            title = "",
            actionIconRes = UIKitIcon.ic_close_16,
            onActionClick = { onFinishClick() },
            ignoreSystemOffset = true,
            showDivider = false,
            backgroundColor = Color.Transparent
        )

        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DAppIcon(icon = remember(data.iconUrl) { data.iconUrl.toUri() })
            Spacer(modifier = Modifier.height(Dimens.offsetMedium))

            MoonTextContentCell(
                title = data.name,
                description = data.host, // TODO replace description
            )
            Spacer(modifier = Modifier.height(Dimens.offsetLarge))

            MoonInfoCell(
                text = stringResource(id = Localization.dapp_disclaimer),
                painter = painterResource(id = UIKitIcon.ic_exclamationmark_circle_16)
            )

            MoonButtonCell(
                onClick = onOpen,
                text = stringResource(id = Localization.open)
            )

            var isChecked by remember { mutableStateOf(false) }

            MoonTextCheckboxCell(
                text = stringResource(id = Localization.do_not_show_again),
                isChecked = isChecked,
                onCheckedChanged = {
                    isChecked = it
                    onChecked(isChecked)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DAppIcon(
    icon: Uri?
) {
    Box(modifier = Modifier.padding(Dimens.offsetMedium)) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(UIKit.colorScheme.background.content),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                MoonAsyncImage(
                    image = icon,
                    size = 96.dp,
                )
            } else {
                Image(
                    painter = painterResource(id = UIKitIcon.ic_globe_56),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(UIKit.colorScheme.icon.secondary)
                )
            }
        }
    }
}
