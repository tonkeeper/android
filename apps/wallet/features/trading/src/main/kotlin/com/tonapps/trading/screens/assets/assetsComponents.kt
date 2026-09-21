package com.tonapps.trading.screens.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tonapps.trading.percentDiffColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import io.tradingapi.models.AssetRefSummary.Verification
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonVerificationBadge
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.TextCell
import ui.painterResource
import ui.theme.UIKit

@Composable
internal fun AssetCell(
    item: AssetItem,
    position: MoonBundlePosition = MoonBundlePosition.Default,
    onClick: () -> Unit = {},
) {
    MoonBundleCell(position = position) {
        TextCell(
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        MoonItemTitle(
                            text = item.symbol,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (item.verification == Verification.trusted) {
                            MoonVerificationBadge()
                        }
                    }
                    MoonItemTitle(text = item.formattedPrice)
                }
            },
            subtitle = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoonItemSubtitle(text = item.name)
                    when (item.verification) {
                        Verification.trusted -> MoonItemSubtitle(
                            text = item.formattedChange,
                            color = item.formattedChange.percentDiffColor(),
                        )
                        Verification.whitelist -> MoonItemSubtitle(
                            text = item.formattedChange,
                            color = item.formattedChange.percentDiffColor(),
                        )
                        Verification.none -> MoonItemSubtitle(
                            text = stringResource(Localization.unverified_token),
                            color = UIKit.colorScheme.accent.orange,
                        )
                        Verification.blacklist -> MoonItemSubtitle(
                            text = stringResource(Localization.scam),
                            color = UIKit.colorScheme.accent.red,
                        )
                    }
                }
            },
            image = {
                MoonItemImage(
                    modifier = Modifier
                        .background(
                            color = UIKit.colorScheme.background.contentTint,
                            shape = CircleShape
                        )
                        .size(44.dp)
                        .clip(CircleShape),
                    image = item.imageUrl,
                    placeholder = painterResource(UIKitIcon.ic_illustration),
                    size = 44.dp
                )
            },
            onClick = onClick,
            minHeight = 76.dp,
        )
    }
}
