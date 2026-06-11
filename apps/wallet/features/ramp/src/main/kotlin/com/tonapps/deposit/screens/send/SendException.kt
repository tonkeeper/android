package com.tonapps.deposit.screens.send

sealed class SendException: Exception() {
    class FailedToSendTransaction: SendException()
    class UnableSendTransaction: SendException()
    class WrongPasscode: SendException()
    class Cancelled: SendException()
    class InvalidComment: SendException()
    class InsufficientBalance: SendException()
}