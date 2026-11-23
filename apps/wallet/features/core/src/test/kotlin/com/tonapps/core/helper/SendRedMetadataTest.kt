package com.tonapps.core.helper

import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.bus.generated.Events.SendNative.SendNativeFeeAsset
import com.tonapps.bus.generated.Events.SendNative.SendNativeFrom
import com.tonapps.core.helper.SendRedMetadata.redSymbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SendRedMetadataTest {

    @Test
    fun anInAppEntryIsNativeUiAndAConnectedAppNamesItself() {
        assertEquals("native_ui", SendRedMetadata.attemptSource(SendNativeFrom.WalletScreen))
        assertEquals("native_ui", SendRedMetadata.attemptSource(SendNativeFrom.JettonScreen))
        assertEquals("native_ui", SendRedMetadata.attemptSource(SendNativeFrom.DeepLink))
        assertEquals("native_ui", SendRedMetadata.attemptSource(SendNativeFrom.QrCode))
        assertEquals("tonconnect_local", SendRedMetadata.attemptSource(SendNativeFrom.TonconnectLocal))
        assertEquals("tonconnect_remote", SendRedMetadata.attemptSource(SendNativeFrom.TonconnectRemote))
        assertEquals("walletconnect", SendRedMetadata.attemptSource(SendNativeFrom.Walletconnect))
    }

    @Test
    fun aJettonTransferCarriesTheSendKeysAndTheNetworkAndSymbol() {
        val json = SendRedMetadata.transfer(
            from = SendNativeFrom.JettonScreen,
            asset = USDT_TON_ID,
            amount = 12.5,
            feeAsset = SendNativeFeeAsset.BatteryCharges,
            tokenSymbol = "USD₮",
        )

        assertEquals(
            """{"amount":12.5,"asset":"$USDT_TON_ID","asset_network":"ton","fee_paid_in":"battery_charges",""" +
                """"source":"jetton_screen","token_symbol":"USD₮"}""",
            json,
        )
    }

    @Test
    fun aTronTransferPaidInTonIsTheBatteryInstantFee() {
        val json = SendRedMetadata.transfer(
            from = SendNativeFrom.WalletScreen,
            asset = "tron/mainnet/trc20/TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
            amount = 3.0,
            feeAsset = SendNativeFeeAsset.BatteryTonInstantFee,
            tokenSymbol = "USD₮",
        )

        assertEquals(
            """{"amount":3.0,"asset":"tron/mainnet/trc20/TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t","asset_network":"tron",""" +
                """"fee_paid_in":"battery_ton_instant_fee","source":"wallet_screen","token_symbol":"USD₮"}""",
            json,
        )
    }

    @Test
    fun anNftTransferLeavesOutTheSymbolAndAnUnknownFee() {
        val json = SendRedMetadata.transfer(
            source = "wallet_screen",
            asset = nftAnalyticsAssetId("0:abc"),
            amount = 1.0,
            feePaidIn = null,
            tokenSymbol = null,
        )

        assertEquals(
            """{"amount":1.0,"asset":"ton/mainnet/nft/0:abc","asset_network":"ton","source":"wallet_screen"}""",
            json,
        )
    }

    @Test
    fun theNetworkIsTheChainSegmentOfTheAssetId() {
        assertEquals("ton", SendRedMetadata.assetNetwork("ton/mainnet/coin"))
        assertEquals("eth", SendRedMetadata.assetNetwork("eth/mainnet/erc20/0xdAC17F958D2ee523a2206206994597C13D831ec7"))
        assertNull(SendRedMetadata.assetNetwork("unknown"))
        assertNull(SendRedMetadata.assetNetwork("/mainnet/coin"))
    }

    @Test
    fun swapSymbolsJoinTheTokenAndItsNetwork() {
        assertEquals("ton_ton", WalletCurrency.TON.redSymbol())
        assertEquals("usdt_ton", WalletCurrency.USDT_TON.redSymbol())
        assertEquals("usdt_trc20", WalletCurrency.USDT_TRON.redSymbol())
    }

    @Test
    fun aSwapReportsBothSidesAndOnlyTellsBatteryApart() {
        val battery = SendRedMetadata.swap(
            from = WalletCurrency.USDT_TON,
            to = WalletCurrency.TON,
            fromAmount = 10.0,
            toAmount = 3.25,
            isBattery = true,
        )
        val ton = SendRedMetadata.swap(
            from = WalletCurrency.TON,
            to = WalletCurrency.USDT_TON,
            fromAmount = 1.0,
            toAmount = 3.0,
            isBattery = false,
        )

        assertEquals(
            """{"asset_network":"ton","fee_paid_in":"battery","from_amount":10.0,"from_token":"usdt_ton",""" +
                """"to_amount":3.25,"to_token":"ton_ton","token_symbol":"USDT"}""",
            battery,
        )
        assertEquals(
            """{"asset_network":"ton","fee_paid_in":"ton","from_amount":1.0,"from_token":"ton_ton",""" +
                """"to_amount":3.0,"to_token":"usdt_ton","token_symbol":"GRAM"}""",
            ton,
        )
    }

    private companion object {
        const val USDT_TON_ID = "ton/mainnet/jetton/0:b113a994b5024a16719f69139328eb759596c38a25f59028b146fecdc3621dfe"
    }
}
