package com.tonapps.wc.models

sealed interface WcEvent {
    data class StateChanged(val state: WcPendingEvent) : WcEvent
    data class Session(val requestId: String, val proposal: WcSessionProposal, val verifyContext: WcValidation) : WcEvent
    data class Request(val requestId: String, val request: WcSessionRequest) : WcEvent

    data class SessionError(val requestId: String, val topic: String, val error: WcError) : WcEvent
    data class RequestError(val requestId: String, val topic: String?, val error: WcError) : WcEvent

    data class Error(val cause: Throwable) : WcEvent
}
