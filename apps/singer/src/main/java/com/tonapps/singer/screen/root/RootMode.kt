package com.tonapps.singer.screen.root

sealed class RootMode {
    data object Default: RootMode()
    data class Select(val body: String, val qr: Boolean): RootMode()
}