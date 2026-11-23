package com.tonapps.deposit.multicoin.moonpay.frame

import com.tonapps.deposit.multicoin.moonpay.api.MoonPayEnvironment
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

class MoonPayFrames {

    enum class Presentation {
        Headless,
        Modal,

        // Drawn by the screen that shows it, not by the host.
        Inline,
    }

    internal class Entry(
        val frame: MoonPayFrame,
        val environment: MoonPayEnvironment,
        val commands: Flow<MoonPayFrameCommand>,
        private val events: SendChannel<MoonPayFrameEvent>,
        private val remove: (Entry) -> Unit,
    ) {

        val presentation: Presentation
            get() = frame.presentation

        val isListening: Boolean
            get() = !events.isClosedForSend

        fun onEvent(event: MoonPayFrameEvent) {
            events.trySend(event)
        }

        fun onFailure(error: MoonPayFrameException) {
            remove(this)
            events.close(error)
        }

        fun onClose() {
            remove(this)
            events.close()
        }
    }

    internal data class State(val host: Any?, val entries: List<Entry>)

    private val mutableState = MutableStateFlow(State(host = null, entries = emptyList()))

    internal val state: StateFlow<State> = mutableState.asStateFlow()

    fun run(
        frame: MoonPayFrame,
        environment: MoonPayEnvironment,
        commands: Flow<MoonPayFrameCommand> = emptyFlow(),
    ): Flow<MoonPayFrameEvent> = callbackFlow {
        val entry = Entry(frame, environment, commands, channel, ::remove)
        do {
            mutableState.first { it.host != null }
        } while (!addWithHost(entry))
        awaitClose { remove(entry) }
    }.buffer(Channel.UNLIMITED)

    internal fun attachHost(token: Any): Boolean {
        var attached = false
        mutableState.update { current ->
            attached = current.host == null
            if (attached) {
                current.copy(host = token)
            } else {
                current
            }
        }
        return attached
    }

    internal fun detachHost(token: Any) {
        var dropped = emptyList<Entry>()
        mutableState.update { current ->
            if (current.host === token) {
                dropped = current.entries
                State(host = null, entries = emptyList())
            } else {
                dropped = emptyList()
                current
            }
        }
        dropped.forEach { it.onFailure(MoonPayFrameException.HostGone()) }
    }

    private fun addWithHost(entry: Entry): Boolean {
        var added = false
        mutableState.update { current ->
            added = current.host != null
            if (added) {
                current.copy(entries = current.entries + entry)
            } else {
                current
            }
        }
        return added
    }

    private fun remove(entry: Entry) {
        mutableState.update { it.copy(entries = it.entries - entry) }
    }
}
