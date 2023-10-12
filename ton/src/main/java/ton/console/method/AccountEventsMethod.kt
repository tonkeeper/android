package com.tonkeeper.ton.console.method

import android.util.Log
import com.tonkeeper.ton.console.model.AccountEventModel
import com.tonkeeper.ton.console.model.AccountEventsModel
import org.json.JSONObject

class AccountEventsMethod(
    address: String,
    limit: Int = 100
): BaseMethod<AccountEventsModel>("accounts/$address/events") {

    init {
        querySet("limit", limit)
    }

    override fun parseJSON(response: JSONObject): AccountEventsModel {
        val events = mutableListOf<AccountEventModel>()
        val eventsArray = response.getJSONArray("events")
        for (i in 0 until eventsArray.length()) {
            val item = eventsArray.getJSONObject(i)
            events.add(AccountEventModel(item))
        }
        return AccountEventsModel(
            events,
            response.getLong("next_from")
        )
    }
}