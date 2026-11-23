package ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun Modifier.platformNestedScrollInterop(): Modifier

@Composable
expect fun DisableNavigationBarContrast()

@Composable
expect fun Modifier.excludeFromBackGestures(): Modifier
