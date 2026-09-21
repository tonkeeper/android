package ui.components.moon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ui.preview.ThemedPreview
import ui.theme.UIKit

private val TrackWidth = 50.dp
private val TrackHeight = 30.dp
private val ThumbSize = 26.dp
private val ThumbHorizontalInset = 2.5.dp
private val ThumbVerticalInset = 2.dp

@Composable
fun MoonSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (() -> Unit)? = null,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) {
            UIKit.colorScheme.buttonPrimary.primaryBackground
        } else {
            UIKit.colorScheme.buttonTertiary.primaryBackground
        },
        label = "moonSwitchTrackColor",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) {
            TrackWidth - ThumbSize - ThumbHorizontalInset
        } else {
            ThumbHorizontalInset
        },
        label = "moonSwitchThumbOffset",
    )

    Box(
        modifier = modifier
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        role = Role.Switch,
                        onValueChange = { onCheckedChange() },
                    )
                } else {
                    Modifier.semantics { role = Role.Switch }
                },
            )
            .size(width = TrackWidth, height = TrackHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset, y = ThumbVerticalInset)
                .size(ThumbSize)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Preview
@Composable
private fun MoonSwitchPreview() {
    ThemedPreview {
        var checked by remember { mutableStateOf(true) }
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MoonSwitch(
                checked = checked,
                onCheckedChange = { checked = !checked },
            )
            MoonSwitch(checked = false)
        }
    }
}
