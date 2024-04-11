package com.tonapps.wallet.data.tonconnect.source

import com.tonapps.network.get
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.tonconnect.entities.DAppManifestEntity

internal class RemoteDataSource(
    private val api: API
) {

    fun loadManifest(url: String): DAppManifestEntity {
        val response = api.defaultHttpClient.get(url)
        return DAppManifestEntity(response)
    }
}