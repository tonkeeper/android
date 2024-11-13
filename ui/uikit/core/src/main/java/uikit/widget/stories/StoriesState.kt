package uikit.widget.stories

data class StoriesState(
    val currentIndex: Int = -1,
    val isAutoSwitchPaused: Boolean = false,
    val lastPauseTime: Long = 0,
)