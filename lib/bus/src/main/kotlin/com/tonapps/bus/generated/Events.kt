package com.tonapps.bus.generated

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
interface Events {

    companion object {
        const val VERSION = "2.7.1"
    }

    interface TransactionSent {

        enum class TransactionSentEventType(val key: String) {
            TonTransfer("TonTransfer"),
            ExtraCurrencyTransfer("ExtraCurrencyTransfer"),
            ContractDeploy("ContractDeploy"),
            JettonTransfer("JettonTransfer"),
            FlawedJettonTransfer("FlawedJettonTransfer"),
            JettonBurn("JettonBurn"),
            JettonMint("JettonMint"),
            NftItemTransfer("NftItemTransfer"),
            Subscribe("Subscribe"),
            UnSubscribe("UnSubscribe"),
            AuctionBid("AuctionBid"),
            NftPurchase("NftPurchase"),
            DepositStake("DepositStake"),
            WithdrawStake("WithdrawStake"),
            WithdrawStakeRequest("WithdrawStakeRequest"),
            ElectionsDepositStake("ElectionsDepositStake"),
            ElectionsRecoverStake("ElectionsRecoverStake"),
            JettonSwap("JettonSwap"),
            SmartContractExec("SmartContractExec"),
            DomainRenew("DomainRenew"),
            Purchase("Purchase"),
            AddExtension("AddExtension"),
            RemoveExtension("RemoveExtension"),
            SetSignatureAllowedAction("SetSignatureAllowedAction"),
            GasRelay("GasRelay"),
            DepositTokenStake("DepositTokenStake"),
            WithdrawTokenStakeRequest("WithdrawTokenStakeRequest"),
            LiquidityDeposit("LiquidityDeposit"),
            Unknown("Unknown")
        }

        /** transaction_sent */
        fun transactionSent(eventType: TransactionSentEventType)
    }


    interface BatteryNative {

        enum class BatteryNativeFrom(val key: String) {
            Wallet("wallet"),
            Settings("settings"),
            TronFees("tron_fees"),
            InsufficientFunds("insufficient_funds"),
            Deeplink("deeplink"),
            Send("send"),
            BatteryBanner("battery_banner")
        }

        enum class BatteryNativeType(val key: String) {
            Crypto("crypto"),
            Fiat("fiat")
        }

        enum class BatteryNativeSize(val key: String) {
            Custom("custom"),
            Small("small"),
            Medium("medium"),
            Large("large")
        }

        /** battery_open */
        fun batteryOpen(from: BatteryNativeFrom)

        /** battery_select */
        fun batterySelect(
            from: BatteryNativeFrom,
            type: BatteryNativeType,
            size: BatteryNativeSize,
            promo: String?,
            jetton: String?
        )

        /** battery_success */
        fun batterySuccess(
            from: BatteryNativeFrom,
            type: BatteryNativeType,
            size: BatteryNativeSize,
            promo: String?,
            jetton: String?
        )
    }


    interface DappBrowser {

        enum class DappBrowserOpenFrom(val key: String) {
            Wallet("wallet"),
            History("history"),
            DeepLink("deep-link"),
            Story("story")
        }

        enum class DappBrowserType(val key: String) {
            Explore("explore"),
            Connected("connected")
        }

        enum class DappSharingCopyFrom(val key: String) {
            Share("Share"),
            CopyLink("Copy link")
        }

        enum class DappAppOpenFrom(val key: String) {
            Banner("banner"),
            Browser("browser"),
            BrowserSearch("browser_search"),
            BrowserConnected("browser_connected"),
            Push("push"),
            Sidebar("sidebar")
        }

        /** dapp_browser_open */
        fun dappBrowserOpen(from: DappBrowserOpenFrom, type: DappBrowserType, location: String)

        /** dapp_pin */
        fun dappPin(url: String, location: String)

        /** dapp_unpin */
        fun dappUnpin(url: String, location: String)

        /** dapp_sharing_copy */
        fun dappSharingCopy(url: String, from: DappSharingCopyFrom)

        /** dapp_app_open */
        fun dappAppOpen(
            from: DappAppOpenFrom,
            url: String,
            appId: String,
            bannerId: String?,
            location: String
        )

        /** dapp_browser_search_open */
        fun dappBrowserSearchOpen(url: String, location: String)

        /** dapp_browser_search_click */
        fun dappBrowserSearchClick(url: String, location: String)
    }


    interface InstallApp {

        /** install_app */
        fun installApp(referrer: String?, deeplink: String?)
    }


    interface LaunchApp {

        /** launch_app */
        fun launchApp()
    }


    interface OnrampsNative {

        enum class OnrampsNativeType(val key: String) {
            Buy("buy"),
            Sell("sell"),
            Swap("swap")
        }

        /** onramp_open */
        fun onrampOpen(from: String)

        /** onramp_enter_amount */
        fun onrampEnterAmount(
            txId: String?,
            type: OnrampsNativeType,
            sellAssetNetwork: String,
            sellAssetSymbol: String,
            sellAmount: Double,
            buyAssetNetwork: String,
            buyAssetSymbol: String,
            buyAmount: Double,
            countryCode: String?
        )

        /** onramp_continue_to_provider */
        fun onrampContinueToProvider(
            txId: String?,
            type: OnrampsNativeType,
            sellAssetNetwork: String,
            sellAssetSymbol: String,
            sellAmount: Double,
            buyAssetNetwork: String,
            buyAssetSymbol: String,
            buyAmount: Double,
            countryCode: String?,
            paymentMethod: String,
            providerName: String,
            providerDomain: String
        )

        /** onramp_success */
        fun onrampSuccess(
            txId: String?,
            type: OnrampsNativeType,
            sellAssetNetwork: String,
            sellAssetSymbol: String,
            sellAmount: Double,
            buyAssetNetwork: String,
            buyAssetSymbol: String,
            buyAmount: Double,
            countryCode: String?,
            paymentMethod: String,
            providerName: String,
            providerDomain: String
        )

        /** onramp_fail */
        fun onrampFail(
            txId: String?,
            type: OnrampsNativeType,
            sellAssetNetwork: String,
            sellAssetSymbol: String,
            sellAmount: Double,
            buyAssetNetwork: String,
            buyAssetSymbol: String,
            buyAmount: Double,
            countryCode: String?,
            paymentMethod: String,
            providerName: String,
            providerDomain: String,
            errorCode: String?,
            errorMessage: String?
        )
    }


    interface SendNative {

        enum class SendNativeFrom(val key: String) {
            WalletScreen("wallet_screen"),
            JettonScreen("jetton_screen"),
            DeepLink("deep_link"),
            TonconnectLocal("tonconnect_local"),
            TonconnectRemote("tonconnect_remote"),
            QrCode("qr_code")
        }

        enum class SendNativeFeePaidIn(val key: String) {
            Ton("ton"),
            Trx("trx"),
            Battery("battery"),
            Gasless("gasless"),
            Free("free")
        }

        /** send_open */
        fun sendOpen(from: SendNativeFrom)

        /** send_click */
        fun sendClick(
            from: SendNativeFrom,
            assetNetwork: String,
            tokenSymbol: String,
            amount: Double
        )

        /** send_confirm */
        fun sendConfirm(
            from: SendNativeFrom,
            assetNetwork: String,
            tokenSymbol: String,
            amount: Double,
            feePaidIn: SendNativeFeePaidIn,
            appId: String?
        )

        /** send_success */
        fun sendSuccess(
            from: SendNativeFrom,
            assetNetwork: String,
            tokenSymbol: String,
            amount: Double,
            feePaidIn: SendNativeFeePaidIn,
            transactionId: String,
            appId: String?
        )

        /** send_failed */
        fun sendFailed(
            from: SendNativeFrom,
            assetNetwork: String,
            tokenSymbol: String,
            amount: Double,
            feePaidIn: SendNativeFeePaidIn,
            errorCode: Int,
            errorMessage: String,
            appId: String?
        )
    }


    interface StakingNative {

        /** staking_open */
        fun stakingOpen(from: String)

        /** staking_plus_input */
        fun stakingPlusInput(
            from: String,
            jettonSymbol: String,
            providerName: String,
            providerDomain: String
        )

        /** staking_plus_confirm */
        fun stakingPlusConfirm(jettonSymbol: String, providerName: String, providerDomain: String)

        /** staking_plus_success */
        fun stakingPlusSuccess(jettonSymbol: String, providerName: String, providerDomain: String)

        /** staking_minus_input */
        fun stakingMinusInput(
            from: String,
            jettonSymbol: String,
            providerName: String,
            providerDomain: String
        )

        /** staking_minus_confirm */
        fun stakingMinusConfirm(jettonSymbol: String, providerName: String, providerDomain: String)

        /** staking_minus_success */
        fun stakingMinusSuccess(jettonSymbol: String, providerName: String, providerDomain: String)
    }


    interface SwapsNative {

        enum class SwapsNativeType(val key: String) {
            Native("native")
        }

        enum class SwapsNativeFeePaidIn(val key: String) {
            Ton("ton"),
            Battery("battery")
        }

        /** swap_open */
        fun swapOpen(type: SwapsNativeType)

        /** swap_click */
        fun swapClick(type: SwapsNativeType, jettonSymbolFrom: String, jettonSymbolTo: String)

        /** swap_confirm */
        fun swapConfirm(
            type: SwapsNativeType,
            feePaidIn: SwapsNativeFeePaidIn,
            jettonSymbolFrom: String,
            jettonSymbolTo: String,
            providerName: String
        )

        /** swap_failed */
        fun swapFailed(
            type: SwapsNativeType,
            errorMessage: String,
            feePaidIn: SwapsNativeFeePaidIn,
            jettonSymbolFrom: String,
            jettonSymbolTo: String,
            providerName: String
        )

        /** swap_success */
        fun swapSuccess(
            type: SwapsNativeType,
            feePaidIn: SwapsNativeFeePaidIn,
            jettonSymbolFrom: String,
            jettonSymbolTo: String,
            providerName: String
        )
    }


    interface TonConnect {

        enum class TonConnectAddressType(val key: String) {
            Raw("raw"),
            Bounce("bounce"),
            NonBounce("non-bounce")
        }

        enum class TonConnectNetworkFeePaid(val key: String) {
            Ton("ton"),
            Gasless("gasless"),
            Battery("battery")
        }

        enum class TonConnectPayloadType(val key: String) {
            Text("text"),
            Binary("binary"),
            Cell("cell")
        }

        /** tc_request */
        fun tcRequest(dappUrl: String)

        /** tc_connect */
        fun tcConnect(dappUrl: String, allowNotifications: Boolean)

        /** tc_view_confirm */
        fun tcViewConfirm(dappUrl: String, addressType: TonConnectAddressType)

        /** tc_send_success */
        fun tcSendSuccess(
            dappUrl: String,
            addressType: TonConnectAddressType,
            networkFeePaid: TonConnectNetworkFeePaid
        )

        /** tc_sign_data_success */
        fun tcSignDataSuccess(dappUrl: String, payloadType: TonConnectPayloadType)
    }
}
