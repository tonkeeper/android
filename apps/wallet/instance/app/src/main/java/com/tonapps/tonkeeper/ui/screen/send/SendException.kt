package com.tonapps.tonkeeper.ui.screen.send

sealed class SendException: Exception() {
    class UnableSendTransaction: SendException()
    class WrongPasscode: SendException()
    class FailedToSendTransaction: SendException()
}