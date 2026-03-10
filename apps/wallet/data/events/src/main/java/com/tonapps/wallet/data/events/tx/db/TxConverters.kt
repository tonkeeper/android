package com.tonapps.wallet.data.events.tx.db

import androidx.room.TypeConverter
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toParcel
import com.tonapps.wallet.data.events.tx.model.TxEvent

object TxConverters {

    @TypeConverter
    @JvmStatic
    fun fromEvent(event: TxEvent) = event.toByteArray()

    @TypeConverter
    @JvmStatic
    fun toEvent(bytes: ByteArray) = bytes.toParcel<TxEvent>()
}