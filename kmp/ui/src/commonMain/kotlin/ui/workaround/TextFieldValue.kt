package ui.workaround

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.TextFieldValue
import ui.text.emptyTextValue
import ui.text.toTextValue


@Composable
fun rememberTextFieldValueSecure(
    changer: CharSequence? = null,
    initValue: (() -> CharSequence)? = null
): MutableState<TextFieldValue> {
    val state = remember {
        mutableStateOf(
            initValue?.invoke()?.toTextValue()
                ?: emptyTextValue()
        )
    }

    if (changer != null && initValue != null) {
        remember(changer) {
            initValue.invoke().toTextValue().also {
                state.value = it
            }
        }
    }

    return state
}

@Composable
fun rememberTextFieldValue(
    changer: CharSequence? = null,
    initValue: (() -> CharSequence?)? = null
): MutableState<TextFieldValue> {
    val state = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            initValue?.invoke()?.toTextValue()
                ?: emptyTextValue()
        )
    }

    if (changer != null && initValue != null) {
        remember(changer) {
            initValue.invoke()?.toTextValue()?.also {
                state.value = it
            }
        }
    }

    return state
}
