package io.batteryapi

import io.batteryapi.apis.DefaultApi
import io.batteryapi.apis.GaslessApi
import io.batteryapi.models.GaslessV2Fee
import io.batteryapi.models.GaslessV2SendRequest
import io.batteryapi.models.GaslessV2SendResponse
import io.batteryapi.models.EvmGas
import io.batteryapi.models.EvmSignedAuthorization
import io.batteryapi.models.IOSBatteryPurchaseStatusTransactionsInnerError
import io.batteryapi.models.MultichainTronSendRequest
import io.batteryapi.models.PurchasesPurchasesInner
import io.batteryapi.models.RechargeMethodsMethodsInner
import io.batteryapi.models.SendRequest
import io.batteryapi.models.TronSendRequest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class BatteryGenerationTest {
    @Test
    fun walletRequestsKeepInterceptorAuthentication() {
        val request = DefaultApi().getStatusRequestConfig(
            tonConnectAuth = "tonconnect-token",
            xWalletId = "wallet-id",
        )

        assertTrue(request.requiresAuthentication)
        assertTrue(request.requiresWalletAuthentication)
        assertEquals("wallet-id", request.walletId)
        assertEquals("wallet-id", request.headers["X-Wallet-ID"])
        assertEquals("tonconnect-token", request.headers["X-TonConnect-Auth"])
    }

    @Test
    fun publicConfigDoesNotRequireCredentials() {
        val request = GaslessApi().gaslessBatteryConfigRequestConfig()

        assertFalse(request.requiresAuthentication)
        assertFalse(request.requiresWalletAuthentication)
    }

    @Test
    fun multichainAndLegacyTronSendBodiesStayDistinct() {
        val legacy = Json.decodeFromString<TronSendRequest>("""{"wallet":"TWallet","energy":12}""")
        val multichain = Json.decodeFromString<SendRequest>("""{"chain":"tron","wallet":"TWallet","energy":12}""")

        assertEquals("TWallet", legacy.wallet)
        assertTrue(multichain is MultichainTronSendRequest)
        assertEquals("TWallet", (multichain as MultichainTronSendRequest).wallet)
        assertTrue(Json.encodeToString(SendRequest.serializer(), multichain).contains("\"chain\":\"tron\""))
        assertFalse(Json.encodeToString(TronSendRequest.serializer(), legacy).contains("\"chain\""))
    }

    @Test
    fun gaslessFeeKeepsItsWireValue() {
        val fee = Json.decodeFromString<GaslessV2Fee>("""{"type":"value","asset_id":"battery/mainnet","data":{"amount":"18"}}""")

        assertEquals(GaslessV2Fee.Type.VALUE, fee.type)
        assertTrue(Json.encodeToString(GaslessV2Fee.serializer(), fee).contains("\"type\":\"value\""))
    }

    @Test
    fun unknownBatteryEnumsDoNotFailDecoding() {
        assertEquals(GaslessV2Fee.Type.unknown, Json.decodeFromString<GaslessV2Fee.Type>("\"future-fee\""))
        assertEquals(GaslessV2SendResponse.Status.unknown, Json.decodeFromString<GaslessV2SendResponse.Status>("\"future-status\""))
        assertEquals(PurchasesPurchasesInner.Type.unknown, Json.decodeFromString<PurchasesPurchasesInner.Type>("\"future-purchase\""))
        assertEquals(RechargeMethodsMethodsInner.Type.unknown, Json.decodeFromString<RechargeMethodsMethodsInner.Type>("\"future-token\""))
        assertEquals(
            IOSBatteryPurchaseStatusTransactionsInnerError.Code.unknown,
            Json.decodeFromString<IOSBatteryPurchaseStatusTransactionsInnerError.Code>("\"future-error\""),
        )
        assertEquals(1, IOSBatteryPurchaseStatusTransactionsInnerError.Code.entries.count { it.value == "unknown" })
    }

    @Test
    fun unknownGasMetadataDoesNotDiscardTheEmulation() {
        val gas = Json.decodeFromString<EvmGas>(
            """{"estimated":"1","charged":"2","gas_price_wei":"3","source":"future-source","gas_price_source":"future-price-source"}""",
        )

        assertEquals(EvmGas.Source.unknown, gas.source)
        assertEquals(EvmGas.GasPriceSource.unknown, gas.gasPriceSource)
        assertEquals("2", gas.charged)
    }

    @Test
    fun signatureParityKeepsItsClosedSet() {
        assertEquals(2, EvmSignedAuthorization.YParity.entries.size)
        assertEquals(EvmSignedAuthorization.YParity._1, Json.decodeFromString<EvmSignedAuthorization.YParity>("1"))
        assertEquals("1", Json.encodeToString(EvmSignedAuthorization.YParity.serializer(), EvmSignedAuthorization.YParity._1))
        assertThrows(kotlinx.serialization.SerializationException::class.java) {
            Json.decodeFromString<EvmSignedAuthorization.YParity>("2")
        }
    }

    @Test
    fun signedEnvelopeKeepsChainKitFieldsUnchanged() {
        val body = """{"operation":{"id":"operation-id","payloads":[{"type":"future/signed","meta":{"data":{"r":"0x01","y_parity":1}}}],"extra":"keep"}}"""
        val request = Json.decodeFromString<GaslessV2SendRequest>(body)

        assertEquals(
            Json.parseToJsonElement(body),
            Json.parseToJsonElement(Json.encodeToString(GaslessV2SendRequest.serializer(), request)),
        )
    }
}
