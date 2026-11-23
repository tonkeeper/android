package com.tonapps.portfolio.screens.wallet.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.tonapps.portfolio.wallet.WalletSectionLimit
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonRichItemImage
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.popup.ActionMenu
import ui.components.popup.ComposeActionItem
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit

private val RowMinHeight = 56.dp
private val ImageSize = 24.dp
private val ImageRing = 2.dp
private val ImageStep = 20.dp
private val EllipsisSize = 28.dp
private val MenuWidth = 160.dp
private val MenuGap = 4.dp
private const val MaxPreviewImages = 2

@Immutable
internal sealed interface SectionMoreImage {

    data class Remote(
        val url: String?,
        val preview: String,
    ) : SectionMoreImage

    data class Local(
        @get:DrawableRes val res: Int,
    ) : SectionMoreImage
}

@Composable
internal fun SectionMoreCell(
    title: String,
    images: List<SectionMoreImage>,
    limit: WalletSectionLimit,
    limitTestTag: String,
    position: MoonBundlePosition,
    onClick: () -> Unit,
    onLimitSelect: (WalletSectionLimit) -> Unit,
    modifier: Modifier = Modifier,
) {
    MoonBundleCell(
        modifier = modifier,
        position = position,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = RowMinHeight)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (images.isNotEmpty()) {
                SectionMoreImages(
                    images = images,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = title,
                    style = UIKit.typography.label1,
                    color = UIKit.colorScheme.text.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    painter = painterResource(UIKitIcon.ic_chevron_right_16),
                    tint = UIKit.colorScheme.icon.tertiary,
                    contentDescription = null,
                )
            }
            SectionLimitButton(
                limit = limit,
                testTag = limitTestTag,
                onLimitSelect = onLimitSelect,
            )
        }
    }
}

@Composable
private fun SectionMoreImages(
    images: List<SectionMoreImage>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        images.take(MaxPreviewImages).forEachIndexed { index, image ->
            Box(
                modifier = Modifier
                    .padding(start = ImageStep * index)
                    .size(ImageSize)
                    .background(UIKit.colorScheme.background.content, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val size = if (index == 0) {
                    ImageSize
                } else {
                    ImageSize - ImageRing * 2
                }
                when (image) {
                    is SectionMoreImage.Remote -> MoonRichItemImage(
                        image = image.url,
                        preview = image.preview,
                        size = size,
                    )
                    is SectionMoreImage.Local -> MoonItemImage(
                        painter = painterResource(image.res),
                        size = size,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLimitButton(
    limit: WalletSectionLimit,
    testTag: String,
    onLimitSelect: (WalletSectionLimit) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val donePainter = painterResource(UIKitIcon.ic_done_16)
    val titleFormat = stringResource(Localization.wallet_section_limit_top)
    val items = remember(limit, donePainter, titleFormat) {
        WalletSectionLimit.entries.map { entry ->
            ComposeActionItem(
                id = entry.name,
                text = titleFormat.format(entry.count),
                iconPainter = donePainter.takeIf { entry == limit },
            )
        }
    }
    Box {
        MoonItemIcon(
            modifier = Modifier
                .semantics { testTagsAsResourceId = true }
                .testTag(testTag),
            painter = painterResource(UIKitIcon.ic_ellipsis_28),
            size = EllipsisSize,
            color = UIKit.colorScheme.icon.secondary,
            onClick = { menuExpanded = true },
        )
        ActionMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            items = items,
            onItemClick = { _, index ->
                menuExpanded = false
                onLimitSelect(WalletSectionLimit.entries[index])
            },
            offset = DpOffset(x = EllipsisSize - MenuWidth, y = MenuGap),
            width = MenuWidth,
        )
    }
}

@Preview
@Composable
private fun SectionMoreCellPreview() {
    ThemedPreview {
        SectionMoreCell(
            title = "More Positions",
            images = listOf(
                SectionMoreImage.Remote(url = null, preview = "USDT"),
                SectionMoreImage.Local(res = UIKitIcon.ic_gram_with_bg),
            ),
            limit = WalletSectionLimit.Top3,
            limitTestTag = "preview_limit",
            position = MoonBundlePosition.Footer,
            onClick = {},
            onLimitSelect = {},
        )
    }
}
