package ui.components.moon.cell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ui.theme.UIKit

class PropertyCellStyle(
    val titleStyle: TextStyle,
    val titleColor: Color,
    val valueStyle: TextStyle,
    val valueColor: Color
) {
    companion object {
        @Composable
        fun default(): PropertyCellStyle {
            return PropertyCellStyle(
                titleStyle = UIKit.typography.body2,
                titleColor = UIKit.colorScheme.text.secondary,
                valueStyle = UIKit.typography.label2,
                valueColor = UIKit.colorScheme.text.primary,
            )
        }
    }
}

@Composable
fun MoonPropertyCell(
    title: String,
    value: CharSequence,
    modifier: Modifier = Modifier,
    style: PropertyCellStyle = PropertyCellStyle.default(),
    maxValueLines: Int = 1,
    onClick: (() -> Unit)? = null,
    valuePrefix: (@Composable () -> Unit)? = null, // TODO make better
    valuePostfix: (@Composable () -> Unit)? = null, // TODO make better
) {
    PropertyCellCustom(
        title = {
            Text(
                text = title,
                style = style.titleStyle,
                color = style.titleColor,
                maxLines = 1
            )
        },
        value = {
            if (valuePrefix != null) {
                valuePrefix()
                Spacer(modifier = Modifier.width(4.dp))
            }

            Row {
                when (value) {
                    is AnnotatedString -> {
                        Text(
                            text = value,
                            style = style.valueStyle,
                            color = style.valueColor,
                            overflow = TextOverflow.MiddleEllipsis,
                            textAlign = TextAlign.End,
                            maxLines = maxValueLines,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    else -> {
                        Text(
                            text = value.toString(),
                            style = style.valueStyle,
                            color = style.valueColor,
                            overflow = TextOverflow.MiddleEllipsis,
                            textAlign = TextAlign.End,
                            maxLines = maxValueLines,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }

                if (valuePostfix != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    valuePostfix()
                }
            }
        },
        modifier = modifier,
        onClick = onClick,
    )
}

@Composable
fun PropertyCellCustom(
    title: @Composable () -> Unit,
    value: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val newModifier = if (modifier === Modifier) {
        Modifier.padding(horizontal = 19.dp, vertical = 12.dp)
    } else {
        modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .run {
                onClick
                    ?.let { clickable(onClick = onClick) }
                    ?: this
            }
            .then(newModifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        title()

        Spacer(
            modifier = Modifier
                .width(8.dp)
        )

        value()
    }
}
