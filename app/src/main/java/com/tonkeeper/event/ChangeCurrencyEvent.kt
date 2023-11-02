package com.tonkeeper.event

import ton.SupportedCurrency
import core.BaseEvent

data class ChangeCurrencyEvent(val value: SupportedCurrency): BaseEvent()