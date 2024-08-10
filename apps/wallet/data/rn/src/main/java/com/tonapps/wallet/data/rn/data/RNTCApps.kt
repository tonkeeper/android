package com.tonapps.wallet.data.rn.data

import org.json.JSONObject

data class RNTCApps(
    val address: String,
    val apps: List<RNTCApp>
) {

    companion object {

        fun toJSON(list: List<RNTCApps>): JSONObject {
            val json = JSONObject()
            for (app in list) {
                json.put(app.address, app.toJSON())
            }
            return json
        }

        fun parse(json: JSONObject?): List<RNTCApps> {
            if (json == null) {
                return emptyList()
            }
            val list = mutableListOf<RNTCApps>()
            val keys = json.keys()
            for (address in keys) {
                val appsJSON = json.getJSONObject(address)
                val appsList = mutableListOf<RNTCApp>()
                for (appId in appsJSON.keys()) {
                    appsList.add(RNTCApp(appsJSON.getJSONObject(appId)))
                }
                list.add(RNTCApps(address, appsList))
            }
            return list
        }
    }

    fun toJSON(): JSONObject {
        val json = JSONObject()
        for (app in apps) {
            json.put(app.appId, app.toJSON())
        }
        return json
    }
}