package com.tonapps.deposit.screens.qr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.blockchain.contract.TokenType
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.core.extensions.externalDrawableUrl
import com.tonapps.core.helper.rememberClipboardManager
import com.tonapps.core.helper.rememberShareManager
import com.tonapps.deposit.common.QrContent
import com.tonapps.extensions.uri
import com.tonapps.mvi.props.observeSafeState
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonTopAppBar
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.MoonTextContentCell
import ui.theme.Dimens
import ui.theme.Shapes
import ui.theme.UIKit
import uikit.navigation.Navigation.Companion.navigation

@Composable
fun QrScreen(
    viewModel: QrAssetFeature,
    showBuyButton: Boolean,
    onFinishClick: () -> Unit,
    onBuyClick: () -> Unit,
) {
    val shareManager = rememberShareManager()
    val clipboardManager = rememberClipboardManager()

    val global by viewModel.state.global.observeSafeState()
    val token = global.data?.token
    val data = global.data
    val context = LocalContext.current
    val errorText = stringResource(id = Localization.unknown_error)
    val toastColor = UIKit.colorScheme.background.contentTint.toArgb()

    LaunchedEffect(global.isError) {
        if (global.isError) {
            context.navigation?.toast(
                message = errorText,
                loading = false,
                color = toastColor,
            )
            onFinishClick()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MoonTopAppBar( // TODO
            title = "",
            navigationIconRes = UIKitIcon.ic_chevron_left_16,
            onNavigationClick = { onFinishClick() },
            ignoreSystemOffset = true,
            showDivider = false,
            backgroundColor = Color.Transparent
        ) {
            if (global.isTabsVisible && token != null) {
                Tabs(
                    token = token,
                    onTabClick = { viewModel.sendAction(QrAssetAction.SelectTab(it)) },
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = Dimens.offsetLarge)
                .padding(bottom = if (showBuyButton) 88.dp else 0.dp)
                .width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (data != null) {
                val token = data.token
                val blockchainImage = remember(token) {
                    when (data.token.blockchain) {
                        Blockchain.TON -> UIKitIcon.ic_ton
                        Blockchain.TRON -> UIKitIcon.ic_tron
                    }
                }

                val name = remember(token) {
                    if (token.isUsdtTrc20) {
                        token.symbol.plus(" ${TokenType.Defined.TRC20.fmt}")
                    } else if (token.isUsdt) {
                        token.symbol.plus(" ${TokenType.Defined.JETTON.fmt}")
                    } else {
                        "${token.name} (${token.symbol})"
                    }
                }

                MoonTextContentCell(
                    title = stringResource(id = Localization.your_address),
                    description = if (token.isUsdtTrc20 || token.isTrx) {
                        stringResource(id = Localization.receive_tron_description, name)
                    } else {
                        stringResource(id = Localization.receive_coin_description, name)
                    }
                )

                Spacer(modifier = Modifier.height(Dimens.offsetLarge))

                QrContent(
                    walletType = data.wallet.type,
                    walletAddress = data.address,
                    content = data.qrContent,
                    tokenImage = remember(token.imageUri, token.isTon) { if (token.isTon) UIKitIcon.ic_ton.uri().toString() else token.imageUri.toString() },
                    blockchainImage = remember(data, token) {
                        if (token.isUsdt || token.isUsdtTrc20) context.externalDrawableUrl(blockchainImage) else null
                    },
                    onCopyClick = { clipboardManager.copy(data.address) },
                    onShareClick = { shareManager.share(data.address) }
                )
            } else if (!global.isError) {
                MoonLoaderCell()
            }
        }

        if (showBuyButton) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(Dimens.offsetMedium)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                MoonAccentButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBuyClick,
                    text = stringResource(id = Localization.buy_ton, token?.symbol ?: ""),
                    size = ButtonSizeLarge,
                    buttonColors = ButtonColorsSecondary
                )
            }
        }
    }
}

@Composable
fun Tabs(
    modifier: Modifier = Modifier,
    token: TokenEntity,
    onTabClick: (tab: QrAssetTab) -> Unit
) {
    val tabs = listOf(QrAssetTab.TON, QrAssetTab.TRON)

    val selectedTab = remember(token) {
        if (token.isTrx || token.isUsdtTrc20) QrAssetTab.TRON else QrAssetTab.TON
    }

    Row(
        modifier = modifier
            .wrapContentWidth()
            .height(40.dp)
            .background(
                shape = Shapes.large,
                color = UIKit.colorScheme.background.content
            )
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selectedTab
            Text(
                text = when (tab) {
                    QrAssetTab.TON -> stringResource(id = Localization.ton)
                    QrAssetTab.TRON -> stringResource(id = Localization.trc20)
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = 32.dp)
                    .clip(Shapes.medium)
                    .background(
                        if (isSelected) {
                            UIKit.colorScheme.buttonPrimary.primaryBackground
                        } else {
                            Color.Transparent
                        }
                    )
                    .wrapContentHeight(align = Alignment.CenterVertically)
                    .padding(horizontal = Dimens.offsetMedium)
                    .clickable { onTabClick(tab) },
                style = UIKit.typography.label2,
                color = UIKit.colorScheme.text.primary
            )
        }
    }
}
