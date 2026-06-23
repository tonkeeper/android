package com.wallet.crypto.trustapp.common.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ui.theme.UIKit

@Composable
fun MoonEditText(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = UIKit.typography.label1,
    placeholder: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    singleLine: Boolean = true, // true by default
    maxLines: Int = Int.MAX_VALUE,
    trailingIcon: @Composable (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: TextFieldColors = EditTextDefaults.colors(),
    paddingValues: PaddingValues = PaddingValues(vertical = 6.dp, horizontal = 2.dp),
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
//            .defaultMinSize(
//                minWidth = TextFieldDefaults.MinWidth,
//                minHeight = 0.dp // To prevent default inflation
//            ),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        singleLine = singleLine,
        maxLines = maxLines,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            TextFieldDefaults.DecorationBox(
                value = value,
                visualTransformation = visualTransformation,
                innerTextField = innerTextField,
                placeholder = placeholder,
                label = null,
                leadingIcon = null,
                trailingIcon = trailingIcon,
                singleLine = singleLine,
                enabled = enabled,
                isError = isError,
                interactionSource = interactionSource,
                colors = colors,
                contentPadding = paddingValues
            )
        }
    )
}

@Composable
fun MoonEditText(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = UIKit.typography.label1,
    placeholder: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
    singleLine: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    trailingIcon: @Composable (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: TextFieldColors = EditTextDefaults.colors(),
    paddingValues: PaddingValues = PaddingValues(vertical = 6.dp, horizontal = 2.dp),
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        singleLine = singleLine,
        maxLines = maxLines,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            TextFieldDefaults.DecorationBox(
                value = value.text,
                visualTransformation = visualTransformation,
                innerTextField = innerTextField,
                placeholder = placeholder,
                label = null,
                leadingIcon = null,
                trailingIcon = trailingIcon,
                singleLine = singleLine,
                enabled = enabled,
                isError = isError,
                interactionSource = interactionSource,
                colors = colors,
                contentPadding = paddingValues
            )
        }
    )
}

private object EditTextDefaults {
    @Composable
    fun colors(): TextFieldColors {
        return TextFieldDefaults.colors(
            disabledContainerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,

            unfocusedTextColor = UIKit.colorScheme.text.primary,
            focusedTextColor = UIKit.colorScheme.text.primary,
            errorTextColor = UIKit.colorScheme.accent.red,
            disabledTextColor = UIKit.colorScheme.text.tertiary,

            errorPlaceholderColor = UIKit.colorScheme.text.secondary,
            disabledPlaceholderColor = UIKit.colorScheme.text.secondary,
            focusedPlaceholderColor = UIKit.colorScheme.text.secondary,
            unfocusedPlaceholderColor = UIKit.colorScheme.text.secondary,

            focusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,

            cursorColor = UIKit.colorScheme.accent.blue,
            errorCursorColor = UIKit.colorScheme.accent.red,
        )
    }
}
