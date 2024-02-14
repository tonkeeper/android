package com.tonapps.tonkeeper.event

import com.tonapps.wallet.data.core.Currency
import core.BaseEvent

data class ChangeCurrencyEvent(val value: Currency): BaseEvent()