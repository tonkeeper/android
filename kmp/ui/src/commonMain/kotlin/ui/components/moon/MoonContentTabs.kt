package ui.components.moon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ui.preview.ThemedPreview
import ui.theme.Dimens
import ui.theme.UIKit

@Immutable
data class MoonTabItem(
    val id: Int,
    val title: String
)

@Composable
fun MoonContentTabs(
    modifier: Modifier = Modifier,
    items: ImmutableList<MoonTabItem>,
    selectedId: Int,
    onSelect: (MoonTabItem) -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .height(40.dp)
            .background(
                shape = UIKit.shapes.large,
                color = UIKit.colorScheme.background.overlayExtraLight
            )
            .padding(4.dp)
    ) {
        items.forEach { item ->
            val isSelected = item.id == selectedId
            Text(
                text = item.title,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 32.dp)
                    .clip(UIKit.shapes.medium)
                    .background(
                        if (isSelected) {
                            UIKit.colorScheme.buttonTertiary.primaryBackground
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { onSelect(item) }
                    .wrapContentHeight(align = Alignment.CenterVertically)
                    .padding(horizontal = Dimens.offsetMedium),
                style = UIKit.typography.label2,
                color = UIKit.colorScheme.text.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview
@Composable
private fun MoonContentTabsPreview() {
    val tabs = persistentListOf(
        MoonTabItem(id = 0, title = "All"),
        MoonTabItem(id = 1, title = "Gainers"),
        MoonTabItem(id = 2, title = "Losers"),
    )
    var selectedId by remember { mutableIntStateOf(0) }
    ThemedPreview {
        MoonContentTabs(
            modifier = Modifier.padding(horizontal = 16.dp),
            items = tabs,
            selectedId = selectedId,
            onSelect = { selectedId = it.id },
        )
    }
}