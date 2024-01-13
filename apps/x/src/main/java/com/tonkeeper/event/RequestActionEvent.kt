package com.tonkeeper.event

import com.tonkeeper.core.tonconnect.models.TCTransaction
import core.BaseEvent

data class RequestActionEvent(
    val transaction: TCTransaction
): BaseEvent()