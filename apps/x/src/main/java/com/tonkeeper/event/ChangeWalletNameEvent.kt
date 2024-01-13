package com.tonkeeper.event

import core.BaseEvent

data class ChangeWalletNameEvent(val address: String, val name: String): BaseEvent()