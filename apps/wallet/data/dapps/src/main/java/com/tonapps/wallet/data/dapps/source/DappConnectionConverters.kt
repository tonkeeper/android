package com.tonapps.wallet.data.dapps.source

import androidx.room.TypeConverter
import com.tonapps.wallet.data.dapps.entities.DappProvider
import com.tonapps.wc.models.WcConnection

object DappConnectionConverters {

    @TypeConverter
    @JvmStatic
    fun fromWcConnection(source: WcConnection?): String? = source?.value

    @TypeConverter
    @JvmStatic
    fun toWcConnection(value: String?): WcConnection? = value?.let { v ->
        WcConnection.entries.firstOrNull { it.value == v }
    }

    @TypeConverter
    @JvmStatic
    fun fromProvider(provider: DappProvider): String = provider.value

    @TypeConverter
    @JvmStatic
    fun toProvider(value: String): DappProvider {
        return runCatching { DappProvider.fromValue(value) }
            .getOrDefault(DappProvider.TonConnect)
    }
}
