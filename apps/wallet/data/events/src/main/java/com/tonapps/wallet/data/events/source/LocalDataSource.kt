package com.tonapps.wallet.data.events.source

import android.content.Context
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toListParcel
import com.tonapps.wallet.data.core.BlobDataSource
import com.tonapps.wallet.data.events.entities.EventEntity

internal class LocalDataSource(context: Context): BlobDataSource<List<EventEntity>>(
    context = context,
    path = "events",
    lruInitialCapacity = 12
) {
    override fun onMarshall(data: List<EventEntity>) = data.toByteArray()

    override fun onUnmarshall(bytes: ByteArray) = bytes.toListParcel<EventEntity>()
}