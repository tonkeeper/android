package ui.components.filter

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ui.components.base.SimpleText
import ui.theme.Dimens
import ui.theme.Shapes
import ui.theme.UIKit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    val colors = UIKit.colorScheme

    val backgroundColor by animateColorAsState(
        targetValue = if (selected) colors.buttonTertiary.primaryBackground else colors.buttonSecondary.primaryBackground,
        label = "bg"
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) colors.buttonTertiary.primaryForeground else colors.buttonSecondary.primaryForeground,
        label = "text"
    )

    val modifier = Modifier
        .clip(Shapes.medium)
        .selectable(
            selected = selected,
            onClick = onClick,
            role = Role.Checkbox,
        )
        .height(Dimens.sizeAction)

    val selectedModifier = if (selected) {
        Modifier.border(
            width = .5f.dp,
            color = UIKit.colorScheme.separator.alternate,
            shape = Shapes.medium
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .background(backgroundColor)
            .then(selectedModifier)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        SimpleText(
            text = text,
            color = textColor,
            style = UIKit.typography.label2
        )
    }

}