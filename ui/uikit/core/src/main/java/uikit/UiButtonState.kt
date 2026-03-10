package uikit

sealed class UiButtonState {
    data class Default(val enabled: Boolean = true) : UiButtonState()
    data object Loading : UiButtonState()
}