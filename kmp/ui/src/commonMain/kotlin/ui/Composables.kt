package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Composable
fun <T> rememberDerivedSaveableState(
    key: String? = null,
    calculation: () -> T
): T {
    val derivedValue by remember { derivedStateOf(calculation) }

    var savedValue by rememberSaveable(key) {
        mutableStateOf(derivedValue)
    }

    LaunchedEffect(derivedValue) {
        savedValue = derivedValue
    }

    return savedValue
}
