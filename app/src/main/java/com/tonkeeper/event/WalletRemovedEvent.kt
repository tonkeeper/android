package com.tonkeeper.event

import core.BaseEvent

data class WalletRemovedEvent(val address: String): BaseEvent()