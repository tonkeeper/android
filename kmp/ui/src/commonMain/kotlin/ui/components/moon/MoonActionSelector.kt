package ui.components.moon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import ui.components.popup.ActionMenu
import ui.components.popup.ActionMenuHorizontalAlignment
import ui.components.popup.ComposeActionItem
import ui.preview.ThemedPreview
import ui.theme.Shapes
import ui.theme.UIKit
import ui.theme.resources.Res
import ui.theme.resources.ic_done_16
import ui.theme.resources.ic_switch_16

enum class MoonActionSelectorStyle {
    Secondary,
    Tertiary,
}

// TODO TK-2330 Refator entirely
@Composable
fun MoonActionSelector(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: MoonActionSelectorStyle = MoonActionSelectorStyle.Tertiary,
    leadingIcon: Painter? = null,
    menuAlignment: ActionMenuHorizontalAlignment = ActionMenuHorizontalAlignment.Center,
    menuOffset: DpOffset = DpOffset(0.dp, 12.dp),
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val donePainter = painterResource(Res.drawable.ic_done_16)
    val menuItems = items.mapIndexed { index, text ->
        ComposeActionItem(
            id = index.toString(),
            text = text,
            iconPainter = if (index == selectedIndex) {
                donePainter
            } else {
                null
            },
        )
    }

    val height: Dp
    val shape: Shape
    val backgroundColor: Color
    val horizontalPadding: Dp
    val spacing: Dp
    when (style) {
        MoonActionSelectorStyle.Secondary -> {
            height = 32.dp
            shape = Shapes.medium
            backgroundColor = UIKit.colorScheme.buttonSecondary.primaryBackground
            horizontalPadding = 12.dp
            spacing = 6.dp
        }

        MoonActionSelectorStyle.Tertiary -> {
            height = 36.dp
            shape = Shapes.large
            backgroundColor = UIKit.colorScheme.buttonTertiary.primaryBackground
            horizontalPadding = 16.dp
            spacing = 8.dp
        }
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .height(height)
                .clip(shape)
                .background(backgroundColor)
                .clickable(onClick = { menuExpanded = !menuExpanded })
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                MoonItemIcon(
                    painter = leadingIcon,
                    color = UIKit.colorScheme.icon.primary,
                )
            }
            Text(
                text = items.getOrNull(selectedIndex).orEmpty(),
                color = UIKit.colorScheme.text.primary,
                style = UIKit.typography.label2,
            )
            MoonItemIcon(
                painter = painterResource(Res.drawable.ic_switch_16),
                color = UIKit.colorScheme.icon.secondary,
            )
        }

        ActionMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            items = menuItems,
            onItemClick = { _, index ->
                menuExpanded = false
                onSelect(index)
            },
            horizontalAlignment = menuAlignment,
            offset = menuOffset,
        )
    }
}

@Preview
@Composable
private fun MoonActionSelectorPreview() {
    ThemedPreview {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MoonActionSelector(
                items = listOf("Trending", "Top Gainers"),
                selectedIndex = 0,
                onSelect = {},
            )
            MoonActionSelector(
                items = listOf("TON", "Ethereum"),
                selectedIndex = 0,
                onSelect = {},
                style = MoonActionSelectorStyle.Secondary,
            )
        }
    }
}
