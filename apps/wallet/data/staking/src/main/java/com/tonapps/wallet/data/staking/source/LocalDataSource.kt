package com.tonapps.wallet.data.staking.source

import android.content.Context
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toListParcel
import com.tonapps.wallet.data.core.BlobDataSource
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import java.util.concurrent.TimeUnit

internal class LocalDataSource(context: Context): BlobDataSource<List<PoolInfoEntity>>(
    context = context,
    path = "staking",
    lruInitialCapacity = 2,
    timeout = TimeUnit.DAYS.toMillis(1)
) {

    override fun onMarshall(data: List<PoolInfoEntity>) = data.toByteArray()

    override fun onUnmarshall(bytes: ByteArray) = bytes.toListParcel<PoolInfoEntity>()
}