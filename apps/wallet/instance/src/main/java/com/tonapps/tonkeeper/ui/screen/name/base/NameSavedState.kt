package com.tonapps.tonkeeper.ui.screen.name.base

import android.graphics.Color
import androidx.lifecycle.SavedStateHandle

class NameSavedState(private val handle: SavedStateHandle) {

    private companion object {
        private const val EMOJI = "emoji"
        private const val NAME = "name"
        private const val COLOR = "color"
    }

    var emoji: CharSequence
        get() = handle.get<String>(EMOJI) ?: "\uD83D\uDC8E"
        set(value) = handle.set(EMOJI, value)

    var name: String
        get() = handle.get<String>(NAME) ?: "Wallet"
        set(value) = handle.set(NAME, value)

    var color: Int
        get() = handle.get<Int>(COLOR) ?: Color.TRANSPARENT
        set(value) = handle.set(COLOR, value)

}