package com.tonapps.tonkeeper.ui.screen.events.compose.history.state

enum class TxLoadingState {
    Refreshing,
    Loading,
    Error,
    LoadingOffset,
    ErrorOffset,
    Empty,
    Ready,
    ReadyAll
}