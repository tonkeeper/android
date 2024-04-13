package com.tonapps.wallet.data.events.source

import android.content.Context
import android.util.Log
import com.tonapps.wallet.api.fromJSON
import com.tonapps.wallet.api.toJSON
import com.tonapps.wallet.data.core.BlobDataSource
import io.tonapi.models.AccountEvents

internal class LocalDataSource(context: Context): BlobDataSource<AccountEvents>(
    context = context,
    path = "events",
    lruInitialCapacity = 12
) {
    override fun onMarshall(data: AccountEvents): ByteArray {
        val json = toJSON(data)
        return json.toByteArray()
    }

    override fun onUnmarshall(bytes: ByteArray): AccountEvents? {
        if (bytes.isEmpty()) {
            return null
        }
        return try {
            val string = String(bytes)
            fromJSON(string)
        } catch (e: Throwable) {
            null
        }
    }
}