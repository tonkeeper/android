package com.tonapps.wallet.data.browser.source

import android.content.Context
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toParcel
import com.tonapps.wallet.data.browser.entities.BrowserDataEntity
import com.tonapps.wallet.data.core.BlobDataSource

internal class LocalDataSource(context: Context): BlobDataSource<BrowserDataEntity>(
    context = context,
    path = "browser_data",
    lruInitialCapacity = 2
) {
    override fun onMarshall(data: BrowserDataEntity) = data.toByteArray()

    override fun onUnmarshall(bytes: ByteArray) = bytes.toParcel<BrowserDataEntity>()
}