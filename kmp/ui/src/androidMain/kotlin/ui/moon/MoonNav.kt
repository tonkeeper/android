package ui.moon

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay

private const val TransactionDuration = 100

@Composable
fun <T : Any> MoonNav(
    backStack: List<T>,
    entryDecorators: List<NavEntryDecorator<T>> = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    ),
    sceneStrategy: SceneStrategy<T> = SinglePaneSceneStrategy(),
    entryProvider: (key: T) -> NavEntry<T>,
) {
    NavDisplay(
        backStack = backStack,
        sceneStrategy = sceneStrategy,
        entryDecorators = entryDecorators,
        transitionSpec = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(TransactionDuration, easing = LinearEasing),
            ) togetherWith slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(TransactionDuration, easing = LinearEasing),
            )
        },
        popTransitionSpec = {
            slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(TransactionDuration, easing = LinearEasing),
            ) togetherWith slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(TransactionDuration, easing = LinearEasing),
            )
        },
        predictivePopTransitionSpec = {
            slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(TransactionDuration, easing = LinearEasing),
            ) togetherWith slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(TransactionDuration, easing = LinearEasing),
            )
        },
        entryProvider = entryProvider,
    )
}