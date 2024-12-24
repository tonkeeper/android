package com.tonapps.wallet.data.token.source

import android.content.Context
import com.tonapps.extensions.prefs
import com.tonapps.extensions.toByteArray
import com.tonapps.extensions.toListParcel
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.data.core.BlobDataSource

internal class LocalDataSource(context: Context): BlobDataSource<List<BalanceEntity>>(
    context = context,
    path = "wallet"
) {

    private val prefs = context.prefs("tokens")

    override fun onMarshall(data: List<BalanceEntity>) = data.toByteArray()

    override fun onUnmarshall(bytes: ByteArray) = bytes.toListParcel<BalanceEntity>()
}