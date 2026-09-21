package com.tonapps.wallet.features.events.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.features.events.data.HistoryNft
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemImage
import ui.painterResource
import ui.theme.Shapes
import ui.theme.UIKit

@Composable
internal fun ActivityNftPreview(
    nft: HistoryNft,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Shapes.medium12)
            .background(UIKit.colorScheme.background.contentTint)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoonItemImage(
            image = nft.imageUrl,
            size = 64.dp,
            shape = RoundedCornerShape(0.dp),
        )
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = nft.name,
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            NftCollectionRow(
                nft = nft,
                textStyle = UIKit.typography.body2,
                verifiedIconTint = UIKit.colorScheme.text.secondary,
            )
        }
    }
}

@Composable
internal fun NftCollectionRow(
    nft: HistoryNft,
    textStyle: TextStyle,
    verifiedIconTint: Color,
    textAlign: TextAlign? = null,
    modifier: Modifier = Modifier,
) {
    val title = when {
        nft.isUnverified -> stringResource(Localization.nft_unverified)
        nft.collectionName.isNotBlank() -> nft.collectionName
        else -> stringResource(Localization.unnamed_collection)
    }
    val titleColor = if (nft.isUnverified) {
        UIKit.colorScheme.accent.orange
    } else {
        UIKit.colorScheme.text.secondary
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = textStyle,
            color = titleColor,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (nft.isVerified) {
            Icon(
                painter = painterResource(UIKitIcon.ic_verification_16),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = verifiedIconTint,
            )
        }
    }
}
