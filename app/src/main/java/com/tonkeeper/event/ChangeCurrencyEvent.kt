package com.tonkeeper.event

import com.tonkeeper.ton.SupportedCurrency
import core.BaseEvent

data class ChangeCurrency(val value: SupportedCurrency): BaseEvent()