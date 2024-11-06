package com.tonapps.wallet.data.rn.data

import androidx.collection.ArrayMap
import org.json.JSONObject

data class RNVaultState(
    val keys: ArrayMap<String, RNDecryptedData> = ArrayMap()
) {

    companion object {

        fun of(list: List<RNDecryptedData>): RNVaultState {
            val keys = ArrayMap<String, RNDecryptedData>()
            for (m in list) {
                keys[m.identifier] = m
            }
            return RNVaultState(keys)
        }

        fun of(json: JSONObject): RNVaultState {
            val keys = ArrayMap<String, RNDecryptedData>()
            for (key in json.keys()) {
                keys[key] = RNDecryptedData(json.getJSONObject(key))
            }
            return RNVaultState(keys)
        }
    }

    val string: String
        get() = toJSON().toString()


    fun getDecryptedData(walletId: String): RNDecryptedData? {
        return keys[walletId]
    }

    fun list(): List<RNDecryptedData> {
        val list = mutableListOf<RNDecryptedData>()
        for (m in keys) {
            list.add(m.value)
        }
        return list
    }

    fun toJSON(): JSONObject {
        val json = JSONObject()
        for (m in keys) {
            json.put(m.key, m.value.toJSON())
        }
        return json
    }
}