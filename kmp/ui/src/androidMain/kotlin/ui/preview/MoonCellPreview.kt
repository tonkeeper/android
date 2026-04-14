package ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonActionIcon
import ui.components.moon.MoonBadge
import ui.components.moon.MoonBadgeButton
import ui.components.moon.MoonCheckbox
import ui.components.moon.MoonChip
import ui.components.moon.MoonContentTabs
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
import ui.components.moon.MoonLoader
import ui.components.moon.MoonLoadingPreviewImage
import ui.components.moon.MoonSpoiler
import ui.components.moon.MoonTabItem
import ui.components.moon.MoonTopAppBar
import ui.components.moon.MoonTopAppBarLarge
import ui.components.moon.MoonTopAppBarSimple
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.MoonBundleTitleCell
import ui.components.moon.cell.MoonButtonCell
import ui.components.moon.cell.MoonButtonCellDefaults
import ui.components.moon.cell.MoonCardCell
import ui.components.moon.cell.MoonDescriptionCell
import ui.components.moon.cell.MoonEmptyCell
import ui.components.moon.cell.MoonErrorCell
import ui.components.moon.cell.MoonInfoCell
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.MoonPropertyCell
import ui.components.moon.cell.MoonRetryCell
import ui.components.moon.cell.MoonSearchCell
import ui.components.moon.cell.MoonSlideConfirmation
import ui.components.moon.cell.MoonSlideConfirmationState
import ui.components.moon.cell.MoonTextCheckboxCell
import ui.components.moon.cell.MoonTextContentCell
import ui.components.moon.cell.MoonTextFieldCell
import ui.components.moon.cell.TextCell
import ui.components.moon.cell.TextCheckCell
import ui.components.moon.cell.TextCopyCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonBadgedBox
import ui.components.moon.list.loadingItem
import ui.components.moon.list.retryItem
import com.wallet.crypto.trustapp.common.ui.components.MoonEditText
import kotlinx.collections.immutable.persistentListOf
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.theme.UIKit
import ui.theme.resources.Res
import ui.theme.resources.ic_done_bold_16

@Preview
@Composable
private fun SliderPreview() {
    ThemedPreview {
        MoonSlideConfirmation(
            state = MoonSlideConfirmationState.Slider,
            title = "Title",
            onConfirm = {},
            onDone = {}
        )
    }
}

@Preview
@Composable
private fun TextCellPreview() {
    ThemedPreview {
        TextCell(
            title = "Title",
            subtitle = "Subtitle",
            image = {
                MoonLoadingPreviewImage()
            },
            content = {
                MoonLabel(text = "Label")
            }
        )
    }
}


@Preview
@Composable
private fun TextCellHyperPreview() {
    ThemedPreview {
        TextCell(
            title = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MoonItemTitle("Too long text so it", maxLines = 3)
                    MoonLabel("TRC-20", colors = MoonLabelDefault.orange())
                }
            },
            subtitle = { MoonItemSubtitle("Subtitle") },
            image = {
                MoonBadgedBox(
                    badge = "1",
                    image = { MoonLoadingPreviewImage() },
                    direction = BadgeDirection.StartTop,
                )
            },
            content = {
                MoonLabel(text = "Label")
            }
        )
    }
}

@Preview
@Composable
private fun MoonAccentButtonCellPreview() {
    ThemedPreview {
        MoonAccentButton(text = "Continue", onClick = {})
    }
}

@Preview
@Composable
private fun MoonLabelPreview() {
    ThemedPreview {
        Column {
            MoonLabel(text = "Label")
            MoonLabel(text = "Label", colors = MoonLabelDefault.blue())
            MoonLabel(text = "Label", colors = MoonLabelDefault.error())
            MoonLabel(text = "Label", colors = MoonLabelDefault.success())
        }
    }
}

@Preview(device = Devices.NEXUS_5)
@Composable
private fun PreviewBadges() {
    ThemedPreview {
        Row(Modifier.padding(15.dp)) {
            MoonBadgedBox(
                badge = "1",
                image = { MoonLoadingPreviewImage() },
                direction = BadgeDirection.EndTop,
            )

            Spacer(modifier = Modifier.width(15.dp))

            MoonBadgedBox(
                badge = "1",
                image = { MoonLoadingPreviewImage() },
                direction = BadgeDirection.StartTop,
            )

            Spacer(modifier = Modifier.width(15.dp))

            MoonBadgedBox(
                badge = "1",
                image = { MoonLoadingPreviewImage() },
                direction = BadgeDirection.EndBottom,
            )

            Spacer(modifier = Modifier.width(15.dp))

            MoonBadgedBox(
                badge = "1",
                image = { MoonLoadingPreviewImage() },
                direction = BadgeDirection.StartBottom,
            )
        }
    }
}

@Preview
@Composable
private fun MoonActionIconPreview() {
    ThemedPreview {
        Box(
            modifier = Modifier
                .background(UIKit.colorScheme.background.page)
                .padding(36.dp)
        ) {
            MoonActionIcon(
                painter = painterResource(Res.drawable.ic_done_bold_16),
                onClick = {},
                tintColor = UIKit.colorScheme.buttonSecondary.primaryForeground,
                backgroundColor = UIKit.colorScheme.buttonSecondary.primaryBackground,
                contentDescription = null,
            )
        }
    }
}

@Preview
@Composable
private fun TextCellNoSubtitlePreview() {
    ThemedPreview {
        TextCell(
            title = "Title only",
            image = {
                MoonBadgedBox(
                    badge = "32",
                    image = {
                        MoonLoadingPreviewImage()
                    },
                )
            },
        )
    }
}

@Preview
@Composable
private fun MoonSearchCellPreview() {
    ThemedPreview {
        MoonSearchCell(
            searchText = remember { mutableStateOf("") },
            onChanged = {},
            error = false
        )
    }
}

@Preview
@Composable
private fun MoonSearchCellWithCancelPreview() {
    ThemedPreview {
        MoonSearchCell(
            searchText = remember { mutableStateOf("query") },
            onChanged = {},
            error = false,
            onCancel = {}
        )
    }
}

@Preview
@Composable
private fun MoonPropertyCellPreview() {
    ThemedPreview {
        MoonPropertyCell(
            title = "Min deposit",
            value = "0.000229 BTC"
        )
    }
}

@Preview
@Composable
private fun TextCopyCellPreview() {
    ThemedPreview {
        TextCopyCell(
            title = "Min deposit",
            subtitle = "0.000229 BTC",
        )
    }
}

@Preview
@Composable
private fun MoonBundleCellSinglePreview() {
    ThemedPreview {
        MoonBundleCell(position = MoonBundlePosition.Default) {
            Text(
                text = "Single cell",
                modifier = Modifier.padding(16.dp),
                color = UIKit.colorScheme.text.primary
            )
        }
    }
}

@Preview
@Composable
private fun MoonDescriptionCellPreview() {
    ThemedPreview {
        MoonDescriptionCell(text = "This is a description that provides additional context below the main content.")
    }
}

@Preview
@Composable
private fun MoonLoaderCellPreview() {
    ThemedPreview {
        MoonLoaderCell()
    }
}

@Preview
@Composable
private fun MoonRetryCellPreview() {
    ThemedPreview {
        MoonRetryCell(
            message = "Failed to load",
            buttonText = "Retry",
            onRetry = {}
        )
    }
}

@Preview
@Composable
private fun MoonEmptyCellPreview() {
    ThemedPreview {
        MoonEmptyCell(
            title = "Nothing here yet",
            subtitle = "Items will appear here once added",
            firstButtonText = "Action 1",
            onFirstClick = {},
            secondButtonText = "Action 2",
            onSecondClick = {}
        )
    }
}

@Preview
@Composable
private fun LazyListItemsPreview() {
    ThemedPreview {
        LazyColumn {
            loadingItem()
            retryItem(
                text = "Something went wrong",
                buttonText = "Retry",
                onRetry = {}
            )
        }
    }
}

@Preview
@Composable
private fun MoonSpoilerPreview() {
    ThemedPreview {
        Box(
            modifier = Modifier
                .size(100.dp, 50.dp)
                .background(Color.DarkGray)
        ) {
            MoonSpoiler()
        }
    }
}

@Preview
@Composable
private fun MoonTextContentCellPreview() {
    ThemedPreview {
        MoonTextContentCell(title = "Title", description = "Description text goes here")
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun MoonTopAppBarPreview() {
    ThemedPreview {
        MoonTopAppBar(title = "Top App Bar")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun MoonTopAppBarLargePreview() {
    ThemedPreview {
        MoonTopAppBarLarge(title = "Top App Bar")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun MoonTextCheckboxCellPreview() {
    ThemedPreview {
        MoonTextCheckboxCell(
            text = "Top App Bar",
            isChecked = true,
            onCheckedChanged = {},
        )
    }
}

@Preview
@Composable
private fun TextCheckCellPreview() {
    ThemedPreview {
        Column {
            TextCheckCell(
                title = "Option A",
                subtitle = "Selected item",
                isChecked = true,
                onCheckedChange = {},
                image = {
                    MoonLoadingPreviewImage()
                },
            )
            TextCheckCell(
                title = "Option B",
                subtitle = "Unselected item",
                isChecked = false,
                onCheckedChange = {},
                image = {
                    MoonLoadingPreviewImage()
                },
            )
        }
    }
}

@Preview
@Composable
private fun MoonCardCellPreview() {
    ThemedPreview {
        MoonCardCell(
            title = "TON",
            subtitle = "The Open Network",
            image = { MoonLoadingPreviewImage() },
        )
    }
}

@Preview
@Composable
private fun MoonInfoCellPreview() {
    ThemedPreview {
        MoonInfoCell(text = "Minimum deposit amount is 0.01 TON")
    }
}

@Preview
@Composable
private fun MoonTextFieldCellPreview() {
    ThemedPreview {
        Column {
            MoonTextFieldCell(
                value = "",
                onValueChange = {},
                hint = "Enter address",
            )
            Spacer(modifier = Modifier.size(8.dp))
            MoonTextFieldCell(
                value = "EQDtFpE...",
                onValueChange = {},
                hint = "Enter address",
            )
            Spacer(modifier = Modifier.size(8.dp))
            MoonTextFieldCell(
                value = "Invalid",
                onValueChange = {},
                hint = "Enter address",
                isError = true,
            )
        }
    }
}

@Preview
@Composable
private fun MoonButtonCellPreview() {
    ThemedPreview {
        Column {
            MoonButtonCell(text = "Continue", onClick = {})
            MoonButtonCell(
                text = "Secondary",
                colors = MoonButtonCellDefaults.ButtonColorsSecondary,
                onClick = {}
            )
            MoonButtonCell(text = "Disabled", enabled = false, onClick = {})
        }
    }
}

@Preview
@Composable
private fun MoonBundleCellPreview() {
    ThemedPreview {
        Column {
            MoonBundleTitleCell(title = "Section")
            MoonBundleCell(position = MoonBundlePosition.Header) {
                Text(
                    text = "Header cell",
                    modifier = Modifier.padding(16.dp),
                    color = UIKit.colorScheme.text.primary
                )
            }
            MoonBundleCell(position = MoonBundlePosition.Middle) {
                Text(
                    text = "Middle cell",
                    modifier = Modifier.padding(16.dp),
                    color = UIKit.colorScheme.text.primary
                )
            }
            MoonBundleCell(position = MoonBundlePosition.Footer) {
                Text(
                    text = "Footer cell",
                    modifier = Modifier.padding(16.dp),
                    color = UIKit.colorScheme.text.primary
                )
            }
        }
    }
}

@Preview
@Composable
private fun MoonBadgeButtonPreview() {
    ThemedPreview {
        Column {
            MoonBadgeButton(text = "Badge")
            Spacer(modifier = Modifier.size(8.dp))
            MoonBadgeButton(
                text = "Clickable Badge",
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun MoonBadgePreview() {
    ThemedPreview {
        Row(Modifier.padding(15.dp)) {
            MoonBadge(text = "1")
            Spacer(modifier = Modifier.width(8.dp))
            MoonBadge(text = "99+")
        }
    }
}

@Preview
@Composable
private fun MoonCheckboxPreview() {
    ThemedPreview {
        Row(Modifier.padding(15.dp)) {
            MoonCheckbox(checked = true, onCheckedChange = {})
            Spacer(modifier = Modifier.width(8.dp))
            MoonCheckbox(checked = false, onCheckedChange = {})
            Spacer(modifier = Modifier.width(8.dp))
            MoonCheckbox(checked = true, enabled = false, onCheckedChange = {})
        }
    }
}

@Preview
@Composable
private fun MoonChipCommonPreview() {
    ThemedPreview {
        Row(Modifier.padding(15.dp)) {
            MoonChip(text = "Selected", selected = true, onClick = {})
            Spacer(modifier = Modifier.width(8.dp))
            MoonChip(text = "Unselected", selected = false, onClick = {})
        }
    }
}

@Preview
@Composable
private fun MoonContentTabsPreview() {
    ThemedPreview {
        MoonContentTabs(
            modifier = Modifier.padding(horizontal = 16.dp),
            items = persistentListOf(
                MoonTabItem(id = 0, title = "All"),
                MoonTabItem(id = 1, title = "Gainers"),
                MoonTabItem(id = 2, title = "Losers"),
            ),
            selectedId = 0,
            onSelect = {},
        )
    }
}

@Preview
@Composable
private fun MoonEditTextPreview() {
    ThemedPreview {
        Column(Modifier.padding(15.dp)) {
            MoonEditText(
                value = "",
                onValueChange = {},
            )
            Spacer(modifier = Modifier.size(8.dp))
            MoonEditText(
                value = "Some text",
                onValueChange = {},
            )
        }
    }
}

@Preview
@Composable
private fun MoonLoaderPreview() {
    ThemedPreview {
        Box(Modifier.padding(15.dp)) {
            MoonLoader()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun MoonTopAppBarSimplePreview() {
    ThemedPreview {
        MoonTopAppBarSimple(title = "Simple Bar", subtitle = "Subtitle")
    }
}

@Preview
@Composable
private fun MoonErrorCellPreview() {
    ThemedPreview {
        MoonErrorCell(text = "Something went wrong")
    }
}