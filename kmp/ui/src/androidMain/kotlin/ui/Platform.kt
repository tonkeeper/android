package ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection

@Composable
actual fun Modifier.platformNestedScrollInterop(): Modifier = this.nestedScroll(rememberNestedScrollInteropConnection())