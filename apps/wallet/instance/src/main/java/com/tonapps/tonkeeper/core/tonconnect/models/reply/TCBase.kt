package com.tonapps.tonkeeper.core.tonconnect.models.reply

import org.json.JSONObject

abstract class TCBase {

    abstract fun toJSON(): JSONObject


    override fun toString(): String {
        return toJSON().toString()
    }

}