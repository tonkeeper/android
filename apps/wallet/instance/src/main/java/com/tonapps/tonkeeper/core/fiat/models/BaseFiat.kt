package com.tonapps.tonkeeper.core.fiat.models

import org.json.JSONObject

abstract class BaseFiat {

    abstract fun toJSON(): JSONObject
}