package com.tonapps.wallet.data.staking.source

import android.content.Context
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toListParcel
import com.tonapps.extensions.toParcel
import com.tonapps.wallet.data.core.BlobDataSource
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import com.tonapps.wallet.data.staking.entities.StakingEntity
import java.util.concurrent.TimeUnit

internal class LocalDataSource(context: Context): BlobDataSource<StakingEntity>(
    context = context,
    path = "staking"
) {

    override fun onMarshall(data: StakingEntity) = data.toByteArray()

    override fun onUnmarshall(bytes: ByteArray) = bytes.toParcel<StakingEntity>()
}