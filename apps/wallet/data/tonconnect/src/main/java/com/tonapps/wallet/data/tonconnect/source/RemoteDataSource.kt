package com.tonapps.wallet.data.tonconnect.source

import android.util.Log
import com.tonapps.network.get
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.tonconnect.entities.DAppManifestEntity
import org.json.JSONObject

internal class RemoteDataSource(
    private val api: API
) {

    fun loadManifest(url: String): DAppManifestEntity {
        val response = api.defaultHttpClient.get(url)
        Log.d("APINewLog", "loadManifest: $response")
        return DAppManifestEntity(JSONObject(response))
    }
}