package com.tonkeeper.fragment.settings.main

import uikit.mvi.UiEffect

sealed class SettingsScreenEffect: UiEffect() {
    data object Logout: SettingsScreenEffect()
}