package ui.components.moon.cell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.tonapps.uikit.icon.UIKitIcon
import ui.components.moon.MoonChevronRight
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLargeItemSubtitle
import ui.components.popup.MoonIconTooltip
import ui.painterResource
import ui.theme.UIKit
import ui.theme.modifiers.modifyIf

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


// TODO to design system
@Composable
fun MoonPropertyBigCell(
    title: CharSequence,
    value: CharSequence,
    valueDescription: CharSequence?,
    onClick: (() -> Unit)? = null,
    valuePrefix: (@Composable () -> Unit)? = null, // TODO make better
    valuePostfix: (@Composable () -> Unit)? = null, // TODO make better
) {
    MoonPropertyBigCell(
        title = {
            MoonLargeItemSubtitle(text = title)
        },
        content = {
            MoonItemTitle(text = value)
        },
        contentDescription = valueDescription?.let {
            { MoonItemSubtitle(text = it) }
        },
        onClick = onClick,
        valuePrefix = valuePrefix,
        valuePostfix = valuePostfix,
    )
}

// TODO to design system
@Composable
fun MoonPropertyBigCell(
    title: @Composable () -> Unit,
    content: @Composable () -> Unit,
    contentDescription: (@Composable () -> Unit)? = null,
    valuePrefix: (@Composable () -> Unit)? = null, // TODO make better
    valuePostfix: (@Composable () -> Unit)? = null, // TODO make better
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .padding(16.dp)
            .modifyIf { onClick?.let { clickable(onClick = it) } },
    ) {
        if (valuePrefix != null) {
            valuePrefix()
            Spacer(modifier = Modifier.width(4.dp))
        }

        title()

        Spacer(Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                content()

                contentDescription?.let {
                    contentDescription.invoke()
                }
            }

            if (valuePostfix != null) {
                Spacer(modifier = Modifier.width(4.dp))
                valuePostfix()
            }
        }
    }
}

@Composable
fun MoonPropertyTitle(
    title: CharSequence,
    subtitle: CharSequence? = null,
    infoTooltip: String? = null,
) {
    InternalPropertyTitle(
        title = title,
        subtitle = subtitle,
        content = infoTooltip?.let { tooltip ->
            { MoonIconTooltip(text = tooltip) }
        },
    )
}

@Composable
fun MoonPropertyTitle(
    title: CharSequence,
    subtitle: CharSequence? = null,
    content: @Composable () -> Unit,
) {
    InternalPropertyTitle(
        title = title,
        subtitle = subtitle,
        content = content,
    )
}

@Composable
private fun InternalPropertyTitle(
    title: CharSequence,
    subtitle: CharSequence?,
    content: (@Composable () -> Unit)?,
) {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoonLargeItemSubtitle(
                text = title,
                color = UIKit.colorScheme.text.secondary,
            )

            content?.invoke()
        }

        subtitle?.let {
            MoonItemSubtitle(text = it)
        }
    }
}

@Composable
fun MoonPropertyValue(
    title: CharSequence,
    subtitle: CharSequence? = null,
    chevron: Boolean = false,
) {
    InternalPropertyValue(
        title = title,
        subtitle = subtitle,
        content = if (chevron) {
            {
                MoonChevronRight()
            }
        } else {
            null
        },
    )
}

@Composable
fun MoonPropertyValue(
    title: CharSequence,
    subtitle: CharSequence? = null,
    content: (@Composable () -> Unit)?,
) {
    InternalPropertyValue(
        title = title,
        subtitle = subtitle,
        content = content,
    )
}

@Composable
private fun InternalPropertyValue(
    title: CharSequence,
    subtitle: CharSequence?,
    content: (@Composable () -> Unit)?,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoonItemTitle(text = title)

            content?.invoke()
        }

        subtitle?.let {
            MoonItemSubtitle(text = it)
        }
    }
}
