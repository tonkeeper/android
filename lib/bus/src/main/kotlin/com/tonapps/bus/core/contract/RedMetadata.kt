package com.tonapps.bus.core.contract

import org.json.JSONObject

class RedMetadata private constructor() {

    private val data = JSONObject()

    private fun build(): String {
        return data.toString()
    }

    fun add(key: String, value: Boolean) {
        data.put(key, value)
    }


    companion object {
        fun builder(builder: RedMetadata.() -> Unit): String {
            val metadata = RedMetadata()
            metadata.builder()
            return metadata.build()
        }
    }
}
