package ui.components.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import ui.theme.UIKit
import ui.theme.Shapes

@Composable
fun TKDetails(
    modifier: Modifier = Modifier,
    details: UiDetails,
    onClick: ((UiDetails.Row) -> Unit)? = null
) {
    Column (
        modifier = modifier
            .fillMaxWidth()
            .clip(Shapes.medium)
            .background(UIKit.colorScheme.background.content)
    ) {
        details.rows.forEachIndexed { index, row ->
            TKDetailsItem(
                row = row,
                divider = index < details.rows.lastIndex,
                onClick = onClick
            )
        }
    }
}