package ui.components.moon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import ui.theme.Dimens
import ui.theme.UIKit
import ui.workaround.isEmpty

///////
@Composable
fun MoonItemIcon(
    painter: Painter,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = UIKit.colorScheme.background.contentTint,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Icon(
        modifier = modifier.defaultIconModifier(size, onClick),
        painter = painter,
        contentDescription = contentDescription,
        tint = color
    )
}


@Composable
fun MoonActionIcon(
    resId: Painter,
    onClick: (() -> Unit)?,
    tintColor: Color,
    backgroundColor: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(Dimens.sizeAction)
            .clip(CircleShape)
            .background(backgroundColor)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = resId,
            contentDescription = contentDescription,
            tint = tintColor
        )
    }
}

///////
@Composable
fun MoonCircleIcon(
    vector: ImageVector,
    modifier: Modifier = Modifier,
    color: Color = UIKit.colorScheme.background.contentTint,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = vector,
            tint = UIKit.colorScheme.icon.secondary,
            contentDescription = null,
        )
    }
}

///////
@Composable
fun MoonTextOutlinePreviewImage(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    textSize: TextUnit = 10.sp,
    shape: Shape = CircleShape,
    color: Color = UIKit.colorScheme.background.contentTint,
    fontWeight: FontWeight? = null
) {
    Box(
        modifier = modifier
            .border(BorderStroke(1.dp, color), shape)
            .size(size),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = UIKit.typography.label1,
            fontWeight = fontWeight,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
            fontSize = textSize,
        )
    }
}

@Composable
fun MoonLoadingPreviewImage(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    color: Color = UIKit.colorScheme.background.contentTint,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .then(modifier)
    )
}

///////
@Composable
fun MoonItemImage(
    painter: Painter,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    shape: Shape = CircleShape,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Image(
        modifier = modifier.defaultImageModifier(size, size, shape, onClick),
        painter = painter,
        contentDescription = contentDescription,
    )
}

@Composable
fun MoonItemImage(
    image: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    shape: Shape = CircleShape,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Image(
        modifier = modifier.defaultImageModifier(size, size, shape, onClick),
        imageVector = image,
        contentDescription = contentDescription,
    )
}

@Composable
fun MoonItemImage(
    image: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    shape: Shape = CircleShape,
    placeholder: Painter? = null,
    error: Painter? = placeholder,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    MoonAsyncImage(
        url = image,
        placeholder = placeholder,
        error = error,
        modifier = modifier.defaultImageModifier(size, size, shape, onClick),
        contentDescription = contentDescription,
        onLoading = onState,
        onError = onState,
        onSuccess = onState,
        contentScale = contentScale,
    )
}

///////
@Composable
fun MoonRichItemImage(
    image: String,
    modifier: Modifier = Modifier,
    preview: String = "",
    size: Dp = 36.dp,
    placeholder: Painter? = null,
    error: Painter? = placeholder,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var isError by remember { mutableStateOf(image.isEmpty()) }
    var isPreview by remember { mutableStateOf(true) }

    Box {
        if (isError && error.isEmpty()) {
            MoonTextOutlinePreviewImage(preview, modifier, size)
        }

        MoonItemImage(
            image = image,
            size = size,
            placeholder = placeholder,
            modifier = modifier,
            onClick = onClick,
            error = error,
            contentDescription = contentDescription,
            onState = { state ->
                when (state) {
                    is AsyncImagePainter.State.Success -> {
                        isPreview = false
                        isError = false
                    }

                    is AsyncImagePainter.State.Error -> {
                        isPreview = false
                        isError = true
                    }

                    else -> Unit
                }
            },
            contentScale = contentScale,
        )

        if (isPreview && placeholder.isEmpty()) {
            MoonLoadingPreviewImage(modifier, size)
        }
    }
}

private fun Modifier.defaultIconModifier(
    size: Dp,
    onClick: (() -> Unit)? = null,
): Modifier = composed {
    size(size)
        .run {
            if (onClick != null) {
                clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    onClick = onClick
                )
            } else {
                this
            }
        }
}


private fun Modifier.defaultImageModifier(
    width: Dp,
    height: Dp,
    shape: Shape,
    onClick: (() -> Unit)? = null,
): Modifier = composed {
    size(width, height)
        .clip(shape)
        .run {
            if (onClick != null) {
                clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    onClick = onClick
                )
            } else {
                this
            }
        }
}

///////

@Composable
fun MoonLargeItemTitle(
    text: CharSequence,
    modifier: Modifier = Modifier,
    color: Color = UIKit.colorScheme.text.primary,
    maxLines: Int = 1,
    inlineTextContent: Map<String, InlineTextContent> = emptyMap(),
) {
    InternalDefaultTitle(text, UIKit.typography.h3, modifier, color, maxLines, inlineTextContent)
}

@Composable
fun MoonItemTitle(
    text: CharSequence,
    modifier: Modifier = Modifier,
    color: Color = UIKit.colorScheme.text.primary,
    maxLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
) {
    InternalDefaultTitle(text, UIKit.typography.label1, modifier, color, maxLines, inlineContent)
}

@Composable
fun MoonSmallItemTitle(
    text: CharSequence,
    modifier: Modifier = Modifier,
    color: Color = UIKit.colorScheme.text.primary,
    maxLines: Int = 1,
    inlineTextContent: Map<String, InlineTextContent> = emptyMap(),
) {
    InternalDefaultTitle(text, UIKit.typography.label2, modifier, color, maxLines, inlineTextContent)
}

@Composable
fun MoonTinyItemTitle(
    text: CharSequence,
    modifier: Modifier = Modifier,
    color: Color = UIKit.colorScheme.text.primary,
    maxLines: Int = 1,
    inlineTextContent: Map<String, InlineTextContent> = emptyMap(),
) {
    InternalDefaultTitle(text, UIKit.typography.label3, modifier, color, maxLines, inlineTextContent)
}

@Composable
private fun InternalDefaultTitle(
    text: CharSequence,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = UIKit.colorScheme.text.primary,
    maxLines: Int = 1,
    inlineTextContent: Map<String, InlineTextContent> = emptyMap(),
) {
    when (text) {
        is AnnotatedString -> Text(
            modifier = modifier,
            text = text,
            inlineContent = inlineTextContent,
            style = style,
            color = color,
            fontWeight = FontWeight.Medium,
            overflow = TextOverflow.Ellipsis,
            maxLines = maxLines,
        )

        else -> Text(
            modifier = modifier,
            text = text.toString(),
            style = style,
            color = color,
            fontWeight = FontWeight.Medium,
            overflow = TextOverflow.Ellipsis,
            maxLines = maxLines,
        )
    }
}

@Composable
fun MoonItemSubtitle(
    text: CharSequence,
    textAlign: TextAlign? = null,
    maxLines: Int = 2,
    fontSize: TextUnit = 12.sp,
    lineHeight: TextUnit = TextUnit.Unspecified,
    color: Color = UIKit.colorScheme.text.secondary,
) {
    when (text) {
        is AnnotatedString -> Text(
            text = text,
            style = UIKit.typography.body2,
            color = color,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            maxLines = maxLines,
            fontSize = fontSize,
            lineHeight = lineHeight,
        )

        else -> Text(
            text = text.toString(),
            style = UIKit.typography.body2,
            color = color,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            maxLines = maxLines,
            fontSize = fontSize,
            lineHeight = lineHeight,
        )
    }
}
