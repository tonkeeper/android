package com.tonapps.tonkeeper.ui.screen.send.main

sealed class SendException: Exception() {
    class FailedToSendTransaction: SendException()
    class UnableSendTransaction: SendException()
    class WrongPasscode: SendException()
    class Cancelled: SendException()
}