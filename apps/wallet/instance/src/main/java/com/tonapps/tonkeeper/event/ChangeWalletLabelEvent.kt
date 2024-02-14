package com.tonapps.tonkeeper.event

import core.BaseEvent

data class ChangeWalletLabelEvent(val address: String): BaseEvent()