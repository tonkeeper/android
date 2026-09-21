package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

@get:Composable
@get:ReadOnlyComposable
val String.shortAddress: String get() {
    val prefixLength = if (startsWith("0x")) {
        2
    } else {
        0
    }
    if (length <= prefixLength + 8) {
        return this
    }
    return "${take(prefixLength + 4)}...${takeLast(4)}"
}


@get:Composable
@get:ReadOnlyComposable
val String.shortData: String get() {
    if (length < 21) {
        return this
    }

    return "${take(8)}...${takeLast(8)}"
}
