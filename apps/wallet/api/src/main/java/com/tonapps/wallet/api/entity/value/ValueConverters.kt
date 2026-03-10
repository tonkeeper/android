package com.tonapps.wallet.api.entity.value

import androidx.room.TypeConverter

object ValueConverters {

    @TypeConverter
    @JvmStatic
    fun fromTimestamp(value: Timestamp) = value.toLong()

    @TypeConverter
    @JvmStatic
    fun toTimestamp(value: Long) = Timestamp(value)

    @TypeConverter
    @JvmStatic
    fun fromBlockchain(value: Blockchain) = value.id

    @TypeConverter
    @JvmStatic
    fun toBlockchain(value: String) = Blockchain.valueOf(value)

    @TypeConverter
    @JvmStatic
    fun fromAddress(value: BlockchainAddress) = value.key

    @TypeConverter
    @JvmStatic
    fun toAddress(value: String) = BlockchainAddress.valueOf(value)

}