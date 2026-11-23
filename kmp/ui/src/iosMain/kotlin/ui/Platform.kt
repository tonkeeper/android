package ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun Modifier.platformNestedScrollInterop(): Modifier = this

@Composable
actual fun DisableNavigationBarContrast() = Unit

@Composable
actual fun Modifier.excludeFromBackGestures(): Modifier = this
