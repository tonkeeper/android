package com.tonapps.tonkeeper.ui.screen.send.main

sealed class SendException: Exception() {
    class UnableSendTransaction: SendException()
    class WrongPasscode: SendException()
    class Cancelled: SendException()
}