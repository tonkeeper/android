package ui.components.moon.cell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wallet.crypto.trustapp.common.ui.components.MoonEditText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import ui.theme.UIKit
import ui.theme.resources.Res
import ui.theme.resources.cancel
import ui.theme.resources.ic_magnifying_glass_16
import ui.theme.resources.ic_xmark_circle_16
import ui.theme.resources.search

@Composable
fun MoonSearchCell(
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(Res.string.search),
    searchText: MutableState<String> = rememberSaveable { mutableStateOf("") },
    onChanged: ((String) -> Unit)? = null,

    enabled: Boolean = true,
    error: Boolean = false,

    cancelTitle: String = stringResource(Res.string.cancel),
    onCancel: (() -> Unit)? = null,

    onSearchFocusChange: ((FocusState) -> Unit)? = null,
    isClearOnSearch: Boolean = false,
    isFocusOnStart: Boolean = false,
    onSearch: ((String) -> Unit)? = onChanged,
    onClick: (() -> Unit)? = null,
) {
    val state = remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .padding(start = 16.dp, end = if (onCancel != null) 0.dp else 16.dp)
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .border(
                    width = 1.5.dp,
                    color = when {
                        state.value -> UIKit.colorScheme.accent.blue
                        error -> UIKit.colorScheme.accent.red
                        else -> UIKit.colorScheme.background.content
                    },
                    shape = UIKit.shapes.large
                )
                .clip(UIKit.shapes.large)
                .background(UIKit.colorScheme.background.content)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            var text by searchText

            val keyboardController = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current

            Icon(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                painter = painterResource(Res.drawable.ic_magnifying_glass_16),
                contentDescription = null,
                tint = UIKit.colorScheme.icon.secondary
            )

            Box {
                MoonEditText(
                    value = text,
                    enabled = if (onClick != null) false else enabled,
                    onValueChange = { value ->
                        text = value
                        onChanged?.invoke(text)
                    },
                    placeholder = placeholder,
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onSearch?.invoke(text)
                            if (isClearOnSearch) {
                                text = ""
                            }
                            focusManager.clearFocus(true)
                            keyboardController?.hide()
                        },
                    ),
                    trailingIcon = {
                        if (text.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    text = ""
                                    onChanged?.invoke(text)
                                }
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_xmark_circle_16),
                                    contentDescription = null,
                                    tint = UIKit.colorScheme.icon.secondary,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    },
                    isFocusOnStart = isFocusOnStart,
                    onSearchFocusChange = {
                        onSearchFocusChange?.invoke(it)
                        state.value = it.isFocused
                    }
                )
            }
        }

        if (onCancel != null) {
            Box(
                modifier = Modifier
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = cancelTitle,
                    color = UIKit.colorScheme.accent.blue,
                )
            }
        }
    }
}

@Composable
fun MoonEditText(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    placeholder: String,
    keyboardActions: KeyboardActions = KeyboardActions(),
    onSearchFocusChange: ((FocusState) -> Unit)? = null,
    isFocusOnStart: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }

    MoonEditText(
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .onFocusChanged {
                onSearchFocusChange?.invoke(it)
            }
            .focusRequester(focusRequester),
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                style = UIKit.typography.body1,
                color = UIKit.colorScheme.text.secondary,
                maxLines = 1
            )
        },
        textStyle = UIKit.typography.body1,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search,
            autoCorrectEnabled = false
        ),
        trailingIcon = trailingIcon,
        keyboardActions = keyboardActions,
        paddingValues = PaddingValues(0.dp),
        maxLines = 1
    )


    if (isFocusOnStart) {
        DisposableEffect(Unit) {
            focusRequester.requestFocus()

            onDispose {
                focusRequester.freeFocus()
            }
        }
    }
}
