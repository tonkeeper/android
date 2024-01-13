package com.tonkeeper.event

import core.BaseEvent

data class ChangeCountryEvent(val country: String): BaseEvent()