package com.tonapps.wallet.data.staking.source

import android.content.Context
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toListParcel
import com.tonapps.wallet.data.core.BlobDataSource
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import com.tonapps.wallet.data.staking.entities.StakingInfoEntity

internal class LocalDataSource(context: Context) {

    private val poolsCache = object : BlobDataSource<List<PoolInfoEntity>>(
        context = context,
        path = "staking_pools"
    ) {
        override fun onMarshall(data: List<PoolInfoEntity>) = data.toByteArray()
        override fun onUnmarshall(bytes: ByteArray) = bytes.toListParcel<PoolInfoEntity>()
    }

    private val infoCache = object : BlobDataSource<List<StakingInfoEntity>>(
        context = context,
        path = "staking_info"
    ) {
        override fun onMarshall(data: List<StakingInfoEntity>) = data.toByteArray()
        override fun onUnmarshall(bytes: ByteArray) = bytes.toListParcel<StakingInfoEntity>()
    }

    fun getPools(key: String): List<PoolInfoEntity>? = poolsCache.getCache(key)

    fun setPools(key: String, value: List<PoolInfoEntity>) = poolsCache.setCache(key, value)

    fun getInfo(key: String): List<StakingInfoEntity>? = infoCache.getCache(key)

    fun setInfo(key: String, value: List<StakingInfoEntity>) = infoCache.setCache(key, value)
}
