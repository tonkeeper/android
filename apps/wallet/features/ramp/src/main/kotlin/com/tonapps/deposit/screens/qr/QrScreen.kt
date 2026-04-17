package com.tonapps.deposit.screens.qr

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import com.tonapps.blockchain.contract.Blockchain
import com.tonapps.blockchain.contract.TokenType
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.core.helper.rememberClipboardManager
import com.tonapps.core.helper.rememberShareManager
import com.tonapps.mvi.props.observeSafeState
import com.tonapps.qr.ui.QRView
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.ButtonColorsSecondary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonTopAppBar
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.theme.Dimens
import ui.theme.Shapes
import ui.theme.UIKit
import ui.utils.uppercased

@Composable
fun QrScreen(
    viewModel: QrAssetFeature,
    showBuyButton: Boolean,
    onFinishClick: () -> Unit,
    onBuyClick: () -> Unit,
) {
    val shareManager = rememberShareManager()
    val clipboardManager = rememberClipboardManager()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val global by viewModel.state.global.observeSafeState()
    val token = global.data?.token
    val data = global.data

    var isTronUsdtShown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.flowWithLifecycle(lifecycle)
            .collect { event ->
                when (event) {
                    QrAssetEvent.ShowTronUsdtEnable -> isTronUsdtShown = true
                }
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
                    } else if (data.showBlockchain && token.isUsdt) {
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
                    tokenImage = token.imageUri,
                    blockchainImage = if (data.showBlockchain && (token.isUsdt || token.isUsdtTrc20)) blockchainImage else null,
                    onCopyClick = { clipboardManager.copy(data.address) },
                    onShareClick = { shareManager.share(data.address) }
                )
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
                    text = stringResource(id = Localization.buy_ton),
                    size = ButtonSizeLarge,
                    buttonColors = ButtonColorsSecondary
                )
            }
        }
    }

    if (isTronUsdtShown && data != null) {
        QrEnableTronDialog(
            isBatteryEnabled = data.isBatteryEnabled,
            onClose = { isTronUsdtShown = false },
            onEnable = { viewModel.sendAction(QrAssetAction.EnableTron) }
        )
    }
}

@Composable
fun QrContent(
    walletType: WalletType,
    walletAddress: String,
    content: String?,
    tokenImage: Uri,
    blockchainImage: Int?,
    onShareClick: () -> Unit,
    onCopyClick: () -> Unit,
) {
    val accentOrangeColor = UIKit.colorScheme.accent.orange
    val backgroundContentTintColor = UIKit.colorScheme.background.contentTint

    val walletSpecificColor = remember(walletType, accentOrangeColor, backgroundContentTintColor) {
        when (walletType) {
            WalletType.Watch -> accentOrangeColor
            else -> backgroundContentTintColor
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 380.dp)
            .padding(horizontal = 16.dp)
            .background(
                color = Color.White,
                shape = RoundedCornerShape(Dimens.cornerLarge)
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (content != null) {
            QrCode(
                content = content,
                tokenImage = tokenImage,
                blockchainImage = blockchainImage,
            )
        }

        Spacer(modifier = Modifier.height(Dimens.offsetMedium))

        Text(
            text = walletAddress,
            style = UIKit.typography.mono,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCopyClick() }
                .semantics(mergeDescendants = false) {
                    testTagsAsResourceId = true
                    testTag = "wallet_address_text"
                }
        )

        if (walletType == WalletType.Watch) {
            Spacer(modifier = Modifier.height(Dimens.offsetMedium))

            Text(
                text = stringResource(id = Localization.watch_only).uppercased(),
                style = UIKit.typography.body4CAPS,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .height(20.dp)
                    .background(
                        color = walletSpecificColor,
                        shape = RoundedCornerShape(Dimens.cornerExtraSmall)
                    )
                    .padding(Dimens.offsetExtraSmall)
            )
        }
    }

    Spacer(modifier = Modifier.height(Dimens.offsetMedium))

    QrActions(
        onShareClick = onShareClick,
        onCopyClick = onCopyClick
    )
}

@Composable
private fun QrActions(
    onShareClick: () -> Unit,
    onCopyClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = { onCopyClick() },
            modifier = Modifier.height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = UIKit.colorScheme.buttonSecondary.primaryBackground,
                contentColor = UIKit.colorScheme.buttonSecondary.primaryForeground,
                disabledContainerColor = UIKit.colorScheme.buttonSecondary.primaryBackgroundDisable,
                disabledContentColor = UIKit.colorScheme.buttonSecondary.primaryForeground.copy(
                    alpha = 0.48f
                )
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = UIKitIcon.ic_copy_16),
                    contentDescription = null,
                    tint = UIKit.colorScheme.icon.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = Localization.copy),
                    style = UIKit.typography.label1
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        IconButton(
            onClick = { onShareClick() },
            modifier = Modifier
                .size(48.dp)
                .background(UIKit.colorScheme.buttonSecondary.primaryBackground, CircleShape)
        ) {
            Icon(
                painter = painterResource(id = UIKitIcon.ic_share_16),
                contentDescription = stringResource(id = Localization.share),
                tint = UIKit.colorScheme.icon.primary
            )
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

@Composable
private fun QrCode(
    content: String,
    tokenImage: Uri,
    blockchainImage: Int?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                QRView(context).apply {
                    withCutout = true
                }
            },
            update = { view ->
                view.withCutout = true
                view.setContent(content)
            }
        )

        Box(
            modifier = Modifier.size(46.dp),
        ) {
            if (blockchainImage != null) {
                MoonCutBadgedBox(
                    direction = BadgeDirection.EndBottom,
                    badge = { MoonItemImage(size = 18.dp, image = blockchainImage) },
                    content = { MoonItemImage(size = 46.dp, image = tokenImage.toString()) }
                )
            } else {
                MoonItemImage(
                    image = tokenImage.toString(),
                    size = 46.dp,
                )
            }
        }
    }
}
