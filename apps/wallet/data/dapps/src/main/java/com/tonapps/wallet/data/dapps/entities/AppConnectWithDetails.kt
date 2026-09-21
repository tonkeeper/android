package com.tonapps.wallet.data.dapps.entities

import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.wallet.data.dapps.source.db.AppRow
import com.tonapps.wallet.data.dapps.source.db.ConnectEntity

data class AppConnectWithDetails(
    val connect: ConnectEntity,
    val app: AppRow,
    val chains: List<Network.Type> = emptyList(),
)
