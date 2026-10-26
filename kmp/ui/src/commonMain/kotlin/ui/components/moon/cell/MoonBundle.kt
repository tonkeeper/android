package ui.components.moon.cell

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.components.moon.MoonChevronRight
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.theme.UIKit
import ui.theme.modifiers.modifyIf

// Set by a standalone MoonBundleCell so a clickable inside it (e.g. BaseTextCell) feeds this source
// instead of scaling itself — the whole island then scales as one. Null in grouped cells.
val LocalMoonBundleScaleSource = compositionLocalOf<MutableInteractionSource?> { null }

enum class MoonBundlePosition {
    Default,
    Middle,
    Header,
    Footer,
    ;

    companion object {
        fun default(size: Int, index: Int): MoonBundlePosition {
            return  when  {
                size == 1 -> MoonBundlePosition.Default
                index == 0 -> MoonBundlePosition.Header
                index == size - 1 -> MoonBundlePosition.Footer
                else -> MoonBundlePosition.Middle
            }
        }
    }
}

fun defaultBundleType(size: Int, index: Int): MoonBundlePosition = MoonBundlePosition.default(size, index)

internal object ObjectAvoirBundleDefaults {
    val CornerHeader: CornerBasedShape
        @Composable @ReadOnlyComposable get() {
            return MaterialTheme.shapes.large.copy(
                bottomEnd = CornerSize(0.0.dp),
                bottomStart = CornerSize(0.0.dp)
            )
        }

    val CornerFooter: CornerBasedShape
        @Composable @ReadOnlyComposable get() {
            return MaterialTheme.shapes.large.copy(
                topEnd = CornerSize(0.0.dp),
                topStart = CornerSize(0.0.dp)
            )
        }
}


@Composable
fun MoonBundleTitleCell(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .modifyIf {
                        onClick?.let {
                            clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = onClick
                            )
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                MoonItemTitle(text = title)

                if (onClick != null) {
                    Spacer(modifier = Modifier.width(2.dp))
                    MoonChevronRight()
                }
            }

            content?.invoke()
        }

        if (description != null) {
            MoonItemSubtitle(text = description)
        }
    }
}

object MoonBundleCellContent {
    val Default = PaddingValues(16.dp)
}

@Composable
fun MoonBundleCell(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = UIKit.colorScheme.background.content,
    position: MoonBundlePosition = MoonBundlePosition.Default,
    contentPadding: PaddingValues? = null,
    margins: PaddingValues = remember { PaddingValues(horizontal = 16.dp) },
    dividerExtraStartInset: Dp? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    // A standalone card scales as a whole on press: the scale indication sits on the outer box
    // (before clip/background) and is driven by a shared interaction source that the clickable
    // inside feeds — the cell's own onClick, or a nested BaseTextCell reading
    // LocalMoonBundleScaleSource. Grouped cells expose no source, so their content scales on its
    // own and the shared card background keeps forming one continuous card.
    val standalone = position == MoonBundlePosition.Default
    val islandSource = if (standalone) remember { MutableInteractionSource() } else null
    CompositionLocalProvider(LocalMoonBundleScaleSource provides islandSource) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(margins)
                .then(
                    if (islandSource != null) Modifier.indication(islandSource, LocalIndication.current) else Modifier
                )
                .run {
                    if (onClick != null && standalone) {
                        clickable(interactionSource = islandSource, indication = null) {
                            onClick()
                        }
                    } else {
                        this
                    }
                }
                .clip(
                    when (position) {
                        MoonBundlePosition.Middle -> RectangleShape
                        MoonBundlePosition.Default -> UIKit.shapes.large
                        MoonBundlePosition.Header -> ObjectAvoirBundleDefaults.CornerHeader
                        MoonBundlePosition.Footer -> ObjectAvoirBundleDefaults.CornerFooter
                    }
                )
                .background(backgroundColor)
                .modifyIf {
                    contentPadding?.let { padding(it) }
                }
                .run {
                    if (onClick != null && !standalone) {
                        clickable {
                            onClick()
                        }
                    } else {
                        this
                    }
                }
                .then(modifier),
        ) {
            content()

            when (position) {
                MoonBundlePosition.Middle,
                MoonBundlePosition.Header -> MoonItemDivider(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .modifyIf { dividerExtraStartInset?.let { inset -> padding(start = inset) } },
                )

                else -> Unit
            }
        }
    }
}
