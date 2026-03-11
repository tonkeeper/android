package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ui.components.moon.BadgeDirection
import ui.components.moon.MoonBadgedBox
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
import ui.components.moon.MoonLoadingPreviewImage
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.MoonBundleTitleCell
import ui.components.moon.cell.MoonDescriptionCell
import ui.components.moon.cell.MoonEmptyCell
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.MoonPropertyCell
import ui.components.moon.cell.MoonRetryCell
import ui.components.moon.cell.MoonSearchCell
import ui.components.moon.cell.TextCell
import ui.components.moon.cell.TextCheckCell
import ui.components.moon.list.loadingItem
import ui.components.moon.list.retryItem
import ui.theme.UIKit

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