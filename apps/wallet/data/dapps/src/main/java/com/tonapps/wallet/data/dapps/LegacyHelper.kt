package com.tonapps.wallet.data.dapps

import android.net.Uri
import androidx.collection.ArrayMap
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.rn.data.RNTCConnection
import com.tonapps.wallet.data.rn.data.RNTCKeyPair

internal object LegacyHelper {

    fun createConnection(connect: AppConnectEntity): RNTCConnection {
        return RNTCConnection(
            type = if (connect.type == AppConnectEntity.Type.External) "remote" else "injected",
            sessionKeyPair = RNTCKeyPair(connect.keyPair),
            clientSessionId = connect.clientId
        )
    }

    fun sortByNetworkAndAccount(
        connections: List<AppConnectEntity>
    ): Pair<ArrayMap<String, List<AppConnectEntity>>, ArrayMap<String, List<AppConnectEntity>>> {
        val mainnetConnectionsMap = ArrayMap<String, List<AppConnectEntity>>()
        val testnetConnectionsMap = ArrayMap<String, List<AppConnectEntity>>()
        for (connection in connections) {
            val list = (if (connection.testnet) {
                testnetConnectionsMap[connection.accountId]
            } else {
                mainnetConnectionsMap[connection.accountId]
            })?.toMutableList() ?: mutableListOf()

            list.add(connection)

            if (connection.testnet) {
                testnetConnectionsMap[connection.accountId] = list
            } else {
                mainnetConnectionsMap[connection.accountId] = list
            }
        }
        return Pair(mainnetConnectionsMap, testnetConnectionsMap)
    }

    fun sortByUrl(
        connections: List<AppConnectEntity>
    ): ArrayMap<Uri, List<AppConnectEntity>> {
        val connectionsByHost = ArrayMap<Uri, List<AppConnectEntity>>()
        for (connection in connections) {
            val list = connectionsByHost[connection.appUrl]?.toMutableList() ?: mutableListOf()
            list.add(connection)
            connectionsByHost[connection.appUrl] = list
        }
        return connectionsByHost
    }
}