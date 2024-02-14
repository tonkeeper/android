package com.tonapps.tonkeeper.event

import com.tonapps.tonkeeper.core.tonconnect.models.TCTransaction
import core.BaseEvent

data class RequestActionEvent(
    val transaction: TCTransaction
): BaseEvent()