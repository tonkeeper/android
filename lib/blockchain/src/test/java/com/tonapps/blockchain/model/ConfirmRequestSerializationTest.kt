package com.tonapps.blockchain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfirmRequestSerializationTest {

    // The sheet can be opened with the request inlined in the fragment arguments, which only works
    // if the dapp context is serializable — it was not, and no caller had hit it yet.
    @Test
    fun dappContextSurvivesRoundTrip() {
        val context = ConfirmContext.Dapp(
            requestId = "42",
            topic = "vaults.fyi",
            name = "Earn",
            url = "vaults.fyi",
            source = ConfirmContext.Dapp.Source.Injected,
        )
        val request = ConfirmRequest(
            data = CommonTransactionData(assetId = "eip155/1/coin", walletId = "wallet"),
            type = ConfirmType.Message("0x68690000", ConfirmType.Message.Type.Personal),
            action = ConfirmAction.Sign,
            context = context,
        )

        val restored = Json.decodeFromString<ConfirmRequest>(Json.encodeToString(request))

        assertEquals(context, restored.context)
    }

    @Test
    fun requestsWithoutSourceDefaultToWalletConnect() {
        val json = """{"requestId":"1","topic":"t","name":"n","url":"u"}"""
        val restored = Json.decodeFromString<ConfirmContext.Dapp>(json)

        assertEquals(ConfirmContext.Dapp.Source.WalletConnect, restored.source)
    }
}
