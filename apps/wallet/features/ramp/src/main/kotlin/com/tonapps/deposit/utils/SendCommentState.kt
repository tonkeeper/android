package com.tonapps.deposit.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

sealed interface CommentError {
    data object LedgerAsciiOnly : CommentError
}

@Stable
class SendCommentState internal constructor(
    private var isLedger: Boolean = false,
) {
    var text by mutableStateOf("")
        private set

    var error: CommentError? by mutableStateOf(null)
        private set

    val isError: Boolean
        get() = error != null

    fun onTextChange(newText: String) {
        text = newText
        error = validate(newText)
    }

    internal fun updateConstraints(isLedger: Boolean) {
        this.isLedger = isLedger
        error = validate(text)
    }

    private fun validate(text: String): CommentError? {
        if (isLedger && text.isNotEmpty() && !text.all { it.code in 32..126 }) {
            return CommentError.LedgerAsciiOnly
        }

        return null
    }

    companion object {
        val Saver: Saver<SendCommentState, String> = Saver(
            save = { it.text },
            restore = { saved -> SendCommentState().also { it.onTextChange(saved) } },
        )
    }
}

@Composable
fun rememberSendCommentState(
    isLedger: Boolean,
): SendCommentState {
    val state = rememberSaveable(saver = SendCommentState.Saver) { SendCommentState(isLedger) }
    LaunchedEffect(isLedger) {
        state.updateConstraints(isLedger)
    }
    return state
}
