package com.tonapps.tonkeeper.ui.screen.events.compose.details.state

import ui.components.details.UiDetails

sealed class UiState() {

    data class Icon(
        val url: String,
        val subicon: String?
    )

    enum class Spam {
        No, Maybe, Spam
    }

    data class Data(
        val hash: String,
        val date: String,
        val imageUrl: String? = null,
        val icons: List<Icon>,
        val title: CharSequence? = null,
        val aboveTitle: CharSequence? = null,
        val subtitle: CharSequence? = null,
        val verifiedSubtitle: Boolean = false,
        val details: UiDetails = UiDetails(),
        val spam: Spam,
        val warningText: String?,
    ): UiState()
}