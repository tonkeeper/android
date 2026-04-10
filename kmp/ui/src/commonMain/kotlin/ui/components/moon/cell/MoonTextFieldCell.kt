package ui.components.moon.cell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import org.jetbrains.compose.resources.painterResource
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonLoader
import ui.theme.UIKit
import ui.theme.resources.Res
import ui.theme.resources.ic_xmark_circle_16
import kotlin.math.roundToInt

@Composable
fun MoonTextFieldCell(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    enabled: Boolean = true,
    isError: Boolean = false,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else 4,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    loading: Boolean = false,
    success: Boolean = false,
    disableClearButton: Boolean = false,
    trailingIcon: @Composable (RowScope.() -> Unit)? = null,
    trailingAction: @Composable (RowScope.() -> Unit)? = null,
    hintColor: Color? = null,
    activeBorderColor: Color? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    MoonTextFieldCellImpl(
        hasText = value.isNotBlank(),
        textField = { mod ->
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = mod,
                enabled = enabled,
                textStyle = UIKit.typography.body1.copy(color = UIKit.colorScheme.text.primary),
                singleLine = singleLine,
                maxLines = maxLines,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                cursorBrush = SolidColor(
                    if (isError) UIKit.colorScheme.accent.red else UIKit.colorScheme.accent.blue
                ),
            )
        },
        onClear = { onValueChange("") },
        modifier = modifier,
        hint = hint,
        enabled = enabled,
        isError = isError,
        loading = loading,
        success = success,
        disableClearButton = disableClearButton,
        trailingIcon = trailingIcon,
        trailingAction = trailingAction,
        hintColor = hintColor,
        activeBorderColor = activeBorderColor,
        interactionSource = interactionSource,
    )
}

@Composable
fun MoonTextFieldCell(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    enabled: Boolean = true,
    isError: Boolean = false,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else 4,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    loading: Boolean = false,
    success: Boolean = false,
    disableClearButton: Boolean = false,
    trailingIcon: @Composable (RowScope.() -> Unit)? = null,
    trailingAction: @Composable (RowScope.() -> Unit)? = null,
    hintColor: Color? = null,
    activeBorderColor: Color? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    MoonTextFieldCellImpl(
        hasText = value.text.isNotBlank(),
        textField = { mod ->
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = mod,
                enabled = enabled,
                textStyle = UIKit.typography.body1.copy(color = UIKit.colorScheme.text.primary),
                singleLine = singleLine,
                maxLines = maxLines,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                cursorBrush = SolidColor(
                    if (isError) UIKit.colorScheme.accent.red else UIKit.colorScheme.accent.blue
                ),
            )
        },
        onClear = { onValueChange(TextFieldValue("")) },
        modifier = modifier,
        hint = hint,
        enabled = enabled,
        isError = isError,
        loading = loading,
        success = success,
        disableClearButton = disableClearButton,
        trailingIcon = trailingIcon,
        trailingAction = trailingAction,
        hintColor = hintColor,
        activeBorderColor = activeBorderColor,
        interactionSource = interactionSource,
    )
}

@Composable
private fun MoonTextFieldCellImpl(
    hasText: Boolean,
    textField: @Composable (Modifier) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    enabled: Boolean = true,
    isError: Boolean = false,
    loading: Boolean = false,
    success: Boolean = false,
    disableClearButton: Boolean = false,
    trailingIcon: @Composable (RowScope.() -> Unit)? = null,
    trailingAction: @Composable (RowScope.() -> Unit)? = null,
    hintColor: Color? = null,
    activeBorderColor: Color? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    // Improved UX: The hint should float up if there is text OR if the field is focused
    val hintReduced = hasText || isFocused

    val borderColor = when {
        isError -> UIKit.colorScheme.field.errorBorder
        isFocused -> activeBorderColor ?: UIKit.colorScheme.field.activeBorder
        else -> UIKit.colorScheme.field.background
    }
    val backgroundColor = when {
        isError -> UIKit.colorScheme.field.errorBackground
        else -> UIKit.colorScheme.field.background
    }

    val hintProgress by animateFloatAsState(
        targetValue = if (hintReduced) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "HintAnimation"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .defaultMinSize(minHeight = 64.dp)
            .border(width = 1.5.dp, color = borderColor, shape = UIKit.shapes.large)
            .clip(UIKit.shapes.large)
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        MoonTextFieldLayout(
            // Passing lambda defers reading state until placement phase (massive perf boost)
            hintProgressProvider = { hintProgress },
            modifier = Modifier.fillMaxWidth(),
            label = if (hint.isNotEmpty()) {
                {
                    Text(
                        text = hint,
                        style = UIKit.typography.body1,
                        color = hintColor ?: UIKit.colorScheme.text.secondary,
                        maxLines = 1,
                    )
                }
            } else null,
            textField = {
                textField(Modifier.fillMaxWidth())
            },
            trailing = {
                val showClear = !disableClearButton && !loading && !success && hasText && isFocused && enabled
                val hasTrailingAction = (disableClearButton || !hasText) && trailingAction != null
                val hasTrailingIcon = (disableClearButton || !hasText) && trailingIcon != null

                if (loading || showClear || hasTrailingAction || hasTrailingIcon) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (loading) MoonLoader(modifier = Modifier.size(16.dp))

                        if (showClear) {
                            MoonItemIcon(
                                painter = painterResource(Res.drawable.ic_xmark_circle_16),
                                color = UIKit.colorScheme.icon.secondary,
                                onClick = onClear,
                            )
                        }
                        if (hasTrailingAction) trailingAction?.invoke(this)
                        if (hasTrailingIcon) trailingIcon?.invoke(this)
                    }
                }
            }
        )
    }
}

@Composable
private fun MoonTextFieldLayout(
    hintProgressProvider: () -> Float,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)?,
    textField: @Composable () -> Unit,
    trailing: @Composable (() -> Unit)?,
) {
    Layout(
        modifier = modifier,
        content = {
            if (label != null) Box(Modifier.layoutId("label")) { label() }
            Box(Modifier.layoutId("textField")) { textField() }
            if (trailing != null) Box(Modifier.layoutId("trailing")) { trailing() }
        }
    ) { measurables, constraints ->
        // 1. Measure Trailing First
        val trailingPlaceable = measurables.firstOrNull { it.layoutId == "trailing" }?.measure(
            constraints.copy(minWidth = 0, minHeight = 0)
        )
        val trailingWidth = trailingPlaceable?.width ?: 0
        val trailingHeight = trailingPlaceable?.height ?: 0

        // 2. Measure Label
        val remainingWidth = maxOf(0, constraints.maxWidth - trailingWidth)
        val labelPlaceable = measurables.firstOrNull { it.layoutId == "label" }?.measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxWidth = remainingWidth)
        )
        val labelHeight = labelPlaceable?.height ?: 0

        // 3. Measure TextField
        val textFieldPlaceable = measurables.first { it.layoutId == "textField" }.measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxWidth = remainingWidth)
        )
        val textFieldHeight = textFieldPlaceable.height

        // 4. Calculate Dimensions Dynamically
        val scaledLabelHeight = labelHeight * 0.75f
        val labelSpacing = 4.dp.roundToPx() // Space between scaled label and text

        // Dynamic layout height: supports infinite multiline gracefully
        val expandedContentHeight = (scaledLabelHeight + labelSpacing + textFieldHeight).roundToInt()
        val collapsedContentHeight = maxOf(labelHeight, textFieldHeight)

        val totalHeight = maxOf(expandedContentHeight, collapsedContentHeight, trailingHeight, constraints.minHeight)

        layout(constraints.maxWidth, totalHeight) {
            // Read State ONLY inside Placement block = no unnecessary remeasurements!
            val progress = hintProgressProvider()

            // Calculate vertical center offset so content feels balanced in minHeight contexts
            val contentOffsetY = maxOf(0f, (totalHeight - expandedContentHeight) / 2f)

            // Trailing Placement (Vertically Centered)
            if (trailingPlaceable != null) {
                trailingPlaceable.placeRelative(
                    x = constraints.maxWidth - trailingPlaceable.width,
                    y = (totalHeight - trailingPlaceable.height) / 2
                )
            }

            // TextField Placement
            val tfCollapsedY = (totalHeight - textFieldHeight) / 2f
            val tfExpandedY = contentOffsetY + scaledLabelHeight + labelSpacing
            val tfY = lerp(tfCollapsedY, tfExpandedY, progress)

            textFieldPlaceable.placeRelative(0, tfY.roundToInt())

            // Label Placement & Scale
            if (labelPlaceable != null) {
                val labelCollapsedY = (totalHeight - labelHeight) / 2f
                val labelExpandedY = contentOffsetY
                val labelY = lerp(labelCollapsedY, labelExpandedY, progress)

                val labelScale = lerp(1f, 0.75f, progress)

                // Place relative WITH layer applies scale physically without measuring again
                labelPlaceable.placeRelativeWithLayer(0, labelY.roundToInt()) {
                    scaleX = labelScale
                    scaleY = labelScale
                    transformOrigin = TransformOrigin(0f, 0f) // Scale originating from Top-Left
                }
            }
        }
    }
}
