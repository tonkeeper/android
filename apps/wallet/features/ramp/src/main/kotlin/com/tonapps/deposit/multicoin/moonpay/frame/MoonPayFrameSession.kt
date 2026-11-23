package com.tonapps.deposit.multicoin.moonpay.frame

import com.tonapps.webview.bridge.InAppBridgeContract.MessageHandler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

internal class MoonPayFrameSession(
    private val onEvent: (MoonPayFrameEvent) -> Unit,
) : MessageHandler<MoonPayFrameEvent, MoonPayFrameCommand> {

    private val commands = Channel<MoonPayFrameCommand>(Channel.UNLIMITED)

    private val beforeHandshake = mutableListOf<MoonPayFrameCommand>()

    private var handshakeReceived = false

    override val responses: Flow<MoonPayFrameCommand> = commands.receiveAsFlow()

    override fun attach() = Unit

    override fun onMessage(message: MoonPayFrameEvent) {
        if (message is MoonPayFrameEvent.Handshake) {
            handshakeReceived = true
            commands.trySend(MoonPayFrameCommand.Ack)
            beforeHandshake.forEach(commands::trySend)
            beforeHandshake.clear()
        }
        onEvent(message)
    }

    fun send(command: MoonPayFrameCommand) {
        if (handshakeReceived) {
            commands.trySend(command)
        } else {
            beforeHandshake += command
        }
    }

    override fun detach(): List<MoonPayFrameCommand> {
        commands.close()
        return emptyList()
    }
}
