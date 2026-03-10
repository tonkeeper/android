package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import ui.components.ActionButtonIcon
import ui.components.SpoilerParticles
import ui.components.TKBadge
import ui.components.TKTopAppBar
import ui.components.TextHeader
import ui.components.bar.top.LargeTopAppBar
import ui.components.base.SimpleText
import ui.components.button.TKButton
import ui.components.moon.MoonCheckbox
import ui.components.moon.MoonLoader
import ui.components.moon.MoonTopAppBar
import ui.components.moon.cell.MoonEmptyCell
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.MoonRetryCell
import ui.theme.resources.Res
import ui.theme.resources.ic_done_bold_16

@Preview
@Composable
private fun CellsPreview() {
    ThemedPreview {
        SimpleText("Text")
    }
}

@Preview
@Composable
private fun MoonCheckboxPreview() {
    ThemedPreview {
        Row {
            MoonCheckbox(checked = false, onCheckedChange = {})
            MoonCheckbox(checked = true, onCheckedChange = {})
            MoonCheckbox(checked = false, onCheckedChange = {}, enabled = false)
            MoonCheckbox(checked = true, onCheckedChange = {}, enabled = false)
        }
    }
}

@Preview
@Composable
private fun MoonTopAppBarPreview() {
    ThemedPreview {
        MoonTopAppBar(title = "Title", subtitle = "Subtitle")
    }
}

@Preview
@Composable
private fun SpoilerParticlesPreview() {
    ThemedPreview {
        Box(
            modifier = Modifier
                .size(100.dp, 50.dp)
                .background(Color.DarkGray)
        ) {
            SpoilerParticles()
        }
    }
}

@Preview
@Composable
private fun TextHeaderPreview() {
    ThemedPreview {
        TextHeader(title = "Title", description = "Description text goes here")
    }
}

@Preview
@Composable
private fun ActionButtonIconPreview() {
    ThemedPreview {
        ActionButtonIcon(
            painter = painterResource(Res.drawable.ic_done_bold_16),
            contentDescription = "Done",
            onClick = {}
        )
    }
}

@Preview
@Composable
private fun TKBadgePreview() {
    ThemedPreview {
        TKBadge(text = "Badge")
    }
}

@Preview
@Composable
private fun MoonLoaderPreview() {
    ThemedPreview {
        Box(
            modifier = Modifier
                .size(100.dp, 50.dp)
        ) {
            MoonLoader()
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun TKTopAppBarPreview() {
    ThemedPreview {
        TKTopAppBar(title = "Top App Bar")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun LargeTopAppBarPreview() {
    ThemedPreview {
        LargeTopAppBar(title = "Top App Bar")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun ButtonPreview() {
    ThemedPreview {
        TKButton(text = "Top App Bar", onClick = {})
    }
}
