package com.tonapps.tonkeeper.ui.screen.qr

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tonapps.qr.ui.QRView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.api.entity.Blockchain
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.localization.Localization
import uikit.compose.AppTheme
import uikit.compose.Dimens
import uikit.compose.UIKit
import uikit.compose.components.AsyncImage
import uikit.compose.components.Header
import uikit.compose.components.ImageShape
import uikit.compose.components.TextHeader

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
            factory = { context ->
                QRView(context).apply {
                    withCutout = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            update = { view ->
                view.withCutout = true
                view.setContent(content)
            }
        )

        Box(
            modifier = Modifier.size(46.dp),
        ) {
            AsyncImage(
                model = tokenImage,
                modifier = Modifier.size(46.dp),
                shape = ImageShape.CIRCLE
            )
            if (blockchainImage != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(3.5.dp, 3.5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(21.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(Color.White)
                            .padding(1.5.dp)
                    ) {
                        AsyncImage(
                            model = blockchainImage,
                            modifier = Modifier.size(18.dp),
                            shape = ImageShape.CIRCLE
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QrContent(
    walletType: Wallet.Type,
    walletAddress: String,
    content: String?,
    tokenImage: Uri,
    blockchainImage: Int?,
    onCopyClick: () -> Unit,
) {
    val accentOrangeColor = UIKit.colors.accentOrange
    val backgroundContentTintColor = UIKit.colors.backgroundContentTint

    val walletSpecificColor = remember(walletType, accentOrangeColor, backgroundContentTintColor) {
        when (walletType) {
            Wallet.Type.Watch -> accentOrangeColor
            else -> backgroundContentTintColor
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.White,
                shape = RoundedCornerShape(Dimens.cornerLarge)
            )
            .padding(30.dp),
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
        )

        if (walletType == Wallet.Type.Watch) {
            Spacer(modifier = Modifier.height(Dimens.offsetMedium))

            Text(
                text = stringResource(id = Localization.watch_only),
                style = UIKit.typography.body4Caps,
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
}

@Composable
fun QrActions(
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
                containerColor = UIKit.colors.buttonSecondaryBackground,
                contentColor = UIKit.colors.buttonSecondaryForeground,
                disabledContainerColor = UIKit.colors.buttonSecondaryBackgroundDisabled,
                disabledContentColor = UIKit.colors.buttonSecondaryForeground.copy(alpha = 0.48f)
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = UIKitIcon.ic_copy_16),
                    contentDescription = null,
                    tint = UIKit.colors.iconPrimary
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
                .background(UIKit.colors.buttonSecondaryBackground, CircleShape)
        ) {
            Icon(
                painter = painterResource(id = UIKitIcon.ic_share_16),
                contentDescription = stringResource(id = Localization.share),
                tint = UIKit.colors.iconPrimary
            )
        }
    }
}

@Composable
fun Tabs(
    modifier: Modifier = Modifier,
    token: TokenEntity,
    onTabClick: (tab: QRViewModel.Tab) -> Unit
) {
    val tabs = listOf(QRViewModel.Tab.TON, QRViewModel.Tab.TRON)

    val selectedTab = remember(token) {
        if (token.isTrc20) QRViewModel.Tab.TRON else QRViewModel.Tab.TON
    }

    Row(
        modifier = modifier
            .wrapContentWidth()
            .height(40.dp)
            .background(
                shape = RoundedCornerShape(20.dp),
                color = UIKit.colors.backgroundContent
            )
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selectedTab
            Text(
                text = when (tab) {
                    QRViewModel.Tab.TON -> stringResource(id = Localization.ton)
                    QRViewModel.Tab.TRON -> stringResource(id = Localization.trc20)
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = 32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) UIKit.colors.buttonPrimaryBackground
                        else Color.Transparent
                    )
                    .wrapContentHeight(align = Alignment.CenterVertically)
                    .padding(horizontal = Dimens.offsetMedium)
                    .clickable { onTabClick(tab) },
                style = UIKit.typography.label2, // эквивалент TextAppearance.Label2
                color = UIKit.colors.textPrimary
            )
        }
    }
}


@Composable
fun QrComposable(
    wallet: WalletEntity,
    tabsVisible: Boolean,
    token: TokenEntity,
    address: String,
    qrContent: String?,
    showBlockchain: Boolean,
    onFinishClick: () -> Unit,
    onShareClick: () -> Unit,
    onCopyClick: () -> Unit,
    onTabClick: (tab: QRViewModel.Tab) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Header(
            title = "",
            navigationIconRes = UIKitIcon.ic_chevron_down_16,
            onNavigationClick = { onFinishClick() },
            ignoreSystemOffset = true,
            showDivider = false,
            backgroundColor = Color.Transparent
        ) {
            if (tabsVisible) {
                Tabs(
                    token = token,
                    onTabClick = onTabClick,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = Dimens.offsetLarge)
                .width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val blockchainImage = remember(token) {
                if (token.blockchain == Blockchain.TRON) {
                    R.drawable.ic_tron
                } else {
                    R.drawable.ic_ton
                }
            }

            val name = if (token.isTrc20) {
                token.symbol.plus(" ${stringResource(id = Localization.trc20)}")
            } else if (showBlockchain && token.isUsdt) {
                token.symbol.plus(" ${stringResource(id = Localization.ton)}")
            } else {
                token.name
            }

            TextHeader(
                title = stringResource(id = Localization.receive_coin, name),
                description = if (token.isTrc20) {
                    stringResource(id = Localization.receive_tron_description, name)
                } else {
                    stringResource(id = Localization.receive_coin_description, name)
                }
            )

            Spacer(modifier = Modifier.height(Dimens.offsetLarge))
            QrContent(
                walletType = wallet.type,
                walletAddress = address,
                content = qrContent,
                tokenImage = token.imageUri,
                blockchainImage = if (showBlockchain && (token.isUsdt || token.isTrc20)) blockchainImage else null,
                onCopyClick = { onCopyClick() }
            )
            Spacer(modifier = Modifier.height(Dimens.offsetMedium))
            QrActions(
                onShareClick = { onShareClick() },
                onCopyClick = { onCopyClick() }
            )
        }
    }
}

@Preview
@Composable
private fun QrComposablePreviewLight() {
    val wallet = WalletEntity.EMPTY
    val token = TokenEntity.TON
    val qrContent = "ton://transfer/${wallet.address}"
    UIKit(theme = AppTheme.BLUE) {
        QrComposable(
            wallet = wallet,
            tabsVisible = true,
            token = token,
            address = wallet.address,
            qrContent = qrContent,
            showBlockchain = true,
            onFinishClick = {},
            onShareClick = {},
            onCopyClick = {},
            onTabClick = {}
        )
    }
}
