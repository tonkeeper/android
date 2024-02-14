package com.tonapps.tonkeeper.event

import core.BaseEvent

data class WalletRemovedEvent(val address: String): BaseEvent()