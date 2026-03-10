package ui

enum class UiPosition {
    Single, Start, Middle, End
}

fun uiPosition(
    index: Int,
    count: Int
): UiPosition {
    if (count == 1) {
        return UiPosition.Single
    }
    return when (index) {
        0 -> UiPosition.Start
        count - 1 -> UiPosition.End
        else -> UiPosition.Middle
    }
}
