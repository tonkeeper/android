package com.tonapps.wallet.data.push.source

import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.push.entities.AppPushEntity

internal class RemoteDataSource(
    private val api: API
) {

    fun getEvents(token: String, accountId: String): List<AppPushEntity> {
        val items = mutableListOf<AppPushEntity>()
        val array = api.getPushFromApps(token, accountId)
        for (i in 0 until array.length()) {
            items.add(AppPushEntity(array.getJSONObject(i)))
        }
        return items.distinctBy { it.dateUnix }
    }
}
