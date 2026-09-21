package com.tonapps.deposit.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.core.components.chainImageUrl
import com.tonapps.core.helper.rememberClipboardManager
import com.tonapps.core.helper.rememberShareManager
import com.tonapps.qr.ui.QRView
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.account.AccountEntity
import com.tonapps.wallet.data.multichain.account.toChainAssetEntity
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.components.moon.container.MoonSurface
import ui.theme.Dimens
import ui.theme.UIKit
import ui.utils.uppercased

@Composable
fun QrDialog(
    account: AccountEntity,
    onClose: () -> Unit = {},
) {
    QrModalSheet(onClose) {
        QrSheetContent(account = account, onClose = onClose)
    }
}

@Composable
fun QrDialog(
    title: String,
    description: String,

    address: String,
    assetImage: String,
    chainImage: String? = null,
    onClose: () -> Unit = {},
) {
    QrModalSheet(onClose) {
        QrSheetContent(title, description, address, assetImage, chainImage, onClose)
    }
}

@Composable
private fun QrModalSheet(
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        MoonSurface {
            content()
        }
    }
}

// The QR sheet body without a sheet container: QrDialog wraps it in a ModalBottomSheet over the
// address list, while ReceiveQrFragment composes it directly as its own bottom sheet's content.
@Composable
fun QrSheetContent(
    account: AccountEntity,
    onClose: () -> Unit = {},
) {
    val asset = remember(account) { account.toChainAssetEntity() }
    val networkName = asset.value.chain.name
    QrSheetContent(
        title = stringResource(Localization.receive_asset_title, networkName),
        description = stringResource(
            Localization.receive_asset_description,
            networkName,
            asset.value.coin.symbol,
        ),
        address = account.displayAddress,
        assetImage = account.chain.coin.chainImageUrl(),
        onClose = onClose,
    )
}

@Composable
fun QrSheetContent(
    title: String,
    description: String,

    address: String,
    assetImage: String,
    chainImage: String? = null,
    onClose: () -> Unit = {},
) {
    val shareManager = rememberShareManager()
    val clipboard = rememberClipboardManager()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MoonTopAppBarSimple(
            title = "",
            navigationIconRes = UIKitIcon.ic_chevron_down_16,
            onNavigationClick = onClose,
            backgroundColor = Color.Transparent
        )

        MoonTextContentCell(
            title = title,
            description = description,
        )

        Spacer(Modifier.height(32.dp))

        QrContent(
            walletType = WalletType.Default,
            walletAddress = address,
            content = address,
            tokenImage = assetImage,
            blockchainImage = chainImage,
            onCopyClick = { clipboard.copy(address) },
            onShareClick = { shareManager.share(address) }
        )
    }
}

@Composable
fun QrContent(
    walletType: WalletType,
    walletAddress: String,
    content: String?,
    tokenImage: String,
    blockchainImage: String?,
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
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
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
                painter = painterResource(id = UIKitIcon.ic_share_box_16),
                contentDescription = stringResource(id = Localization.share),
                tint = UIKit.colorScheme.icon.primary
            )
        }
    }
}

@Composable
private fun QrCode(
    content: String,
    tokenImage: String,
    blockchainImage: String?,
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
                    content = { MoonItemImage(size = 46.dp, image = tokenImage) }
                )
            } else {
                MoonItemImage(
                    image = tokenImage,
                    size = 46.dp,
                )
            }
        }
    }
}
