package com.tonapps.bus.generated

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
interface Events {

    companion object {
        const val VERSION = "6.0.1"
    }

    interface AssetScreen {

        enum class AssetScreenFrom(val key: String) {
            WalletScreen("wallet_screen"),
            DeepLink("deep_link"),
            QrCode("qr_code"),
            TradeScreen("trade_screen")
        }

        enum class AssetScreenWalletMode(val key: String) {
            Multi("multi"),
            Single("single")
        }

        enum class AssetScreenButton(val key: String) {
            Buy("buy"),
            Sell("sell"),
            Send("send"),
            Receive("receive")
        }

        /** asset_view */
        fun assetView(from: AssetScreenFrom, asset: String, walletMode: AssetScreenWalletMode)

        /** asset_button_click */
        fun assetButtonClick(button: AssetScreenButton, asset: String)
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
            DeepLink("deep_link"),
            Story("story")
        }

        enum class DappBrowserType(val key: String) {
            Explore("explore"),
            Connected("connected")
        }

        enum class DappBrowserAssetChain(val key: String) {
            Ton("ton"),
            Eth("eth"),
            Base("base"),
            Arb("arb"),
            Bnb("bnb"),
            Pol("pol"),
            Sol("sol"),
            Tron("tron"),
            Btc("btc"),
            Ltc("ltc"),
            Doge("doge"),
            Bch("bch"),
            Multichain("multichain")
        }

        enum class DappSharingCopyFrom(val key: String) {
            Share("share"),
            CopyLink("copy_link")
        }

        enum class DappAppClickFrom(val key: String) {
            Banner("banner"),
            Browser("browser"),
            BrowserSearch("browser_search"),
            BrowserConnected("browser_connected"),
            Push("push"),
            Sidebar("sidebar"),
            DeepLink("deep_link")
        }

        /** dapp_browser_open */
        fun dappBrowserOpen(from: DappBrowserOpenFrom, type: DappBrowserType, location: String)

        /** dapp_browser_tab_click */
        fun dappBrowserTabClick(type: DappBrowserType, location: String)

        /** dapp_pin */
        fun dappPin(url: String, assetChain: DappBrowserAssetChain, location: String)

        /** dapp_unpin */
        fun dappUnpin(url: String, assetChain: DappBrowserAssetChain, location: String)

        /** dapp_sharing_copy */
        fun dappSharingCopy(
            url: String,
            assetChain: DappBrowserAssetChain,
            from: DappSharingCopyFrom,
            location: String
        )

        /** dapp_app_click */
        fun dappAppClick(
            from: DappAppClickFrom,
            url: String,
            assetChain: DappBrowserAssetChain,
            appId: String,
            bannerId: String?,
            location: String
        )

        /** dapp_app_loaded */
        fun dappAppLoaded(
            from: DappAppClickFrom,
            url: String,
            assetChain: DappBrowserAssetChain,
            appId: String,
            bannerId: String?,
            location: String
        )

        /** dapp_browser_search_open */
        fun dappBrowserSearchOpen(url: String, location: String)

        /** dapp_browser_search_click */
        fun dappBrowserSearchClick(url: String, location: String)
    }


    interface DepositFlow {

        enum class DepositFlowFrom(val key: String) {
            WalletScreen("wallet_screen"),
            JettonScreen("jetton_screen"),
            DeepLink("deep_link"),
            QrCode("qr_code")
        }

        enum class DepositFlowAddFundsOption(val key: String) {
            ReceiveTokens("receive_tokens"),
            BuyWithFiat("buy_with_fiat"),
            BuyTonWithCrypto("buy_ton_with_crypto"),
            BuyWithStablecoins("buy_with_stablecoins"),
            BuyWithP2pMarket("buy_with_p2p_market")
        }

        enum class DepositFlowNetwork(val key: String) {
            TON("TON"),
            TRC20("TRC20")
        }

        enum class DepositFlowBuyAsset(val key: String) {
            TonMainnetCoin("ton/mainnet/coin")
        }

        /** deposit_started */
        fun depositStarted(from: DepositFlowFrom, availableOptions: String)

        /** deposit_option_click */
        fun depositOptionClick(from: DepositFlowFrom, addFundsOption: DepositFlowAddFundsOption)

        /** deposit_view_receive_tokens */
        fun depositViewReceiveTokens(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            network: DepositFlowNetwork
        )

        /** deposit_view_p2p_alert */
        fun depositViewP2pAlert(from: DepositFlowFrom, addFundsOption: DepositFlowAddFundsOption)

        /** deposit_continue_to_p2p_market */
        fun depositContinueToP2pMarket(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption
        )

        /** deposit_view_buy_ton_with_crypto */
        fun depositViewBuyTonWithCrypto(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            buyAsset: DepositFlowBuyAsset,
            availableOptions: String
        )

        /** deposit_view_send_asset */
        fun depositViewSendAsset(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            sellAsset: String,
            buyAsset: String
        )

        /** deposit_view_qr_code */
        fun depositViewQrCode(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            sellAsset: String,
            buyAsset: String
        )

        /** deposit_view_fiat_choose_asset */
        fun depositViewFiatChooseAsset(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            availableOptions: String
        )

        /** deposit_click_fiat_asset */
        fun depositClickFiatAsset(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            buyAsset: String
        )

        /** deposit_view_fiat_payment_method */
        fun depositViewFiatPaymentMethod(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            buyAsset: String,
            sellAsset: String,
            availableOptions: String
        )

        /** deposit_click_fiat_payment_method */
        fun depositClickFiatPaymentMethod(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            buyAsset: String,
            sellAsset: String,
            paymentMethod: String
        )

        /** deposit_view_ramp_insert_amount */
        fun depositViewRampInsertAmount(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            buyAsset: String,
            sellAsset: String,
            paymentMethod: String,
            providerName: String
        )

        /** deposit_click_ramp_insert_amount_continue */
        fun depositClickRampInsertAmountContinue(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            buyAsset: String,
            sellAsset: String,
            paymentMethod: String,
            providerName: String,
            amount: Double
        )

        /** deposit_view_ramp_alert */
        fun depositViewRampAlert(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            buyAsset: String,
            sellAsset: String,
            paymentMethod: String,
            providerName: String,
            amount: Double
        )

        /** deposit_continue_to_ramp_provider */
        fun depositContinueToRampProvider(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            buyAsset: String,
            sellAsset: String,
            paymentMethod: String,
            providerName: String,
            amount: Double,
            txId: String
        )

        /** deposit_view_choose_stablecoin */
        fun depositViewChooseStablecoin(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            availableOptions: String
        )

        /** deposit_click_stablecoin */
        fun depositClickStablecoin(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            buyAsset: String
        )

        /** deposit_view_stablecoin_payment_method */
        fun depositViewStablecoinPaymentMethod(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            buyAsset: String,
            availableOptions: String
        )

        /** deposit_click_stablecoin_payment_method */
        fun depositClickStablecoinPaymentMethod(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            buyAsset: String,
            stablecoinSymbol: String
        )

        /** deposit_view_choose_network */
        fun depositViewChooseNetwork(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            buyAsset: String,
            stablecoinSymbol: String,
            availableOptions: String
        )

        /** deposit_click_network */
        fun depositClickNetwork(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            buyAsset: String,
            stablecoinSymbol: String,
            sellAsset: String
        )
    }


    interface HomeBanner {

        enum class HomeBannerAction(val key: String) {
            Deposit("deposit"),
            Withdraw("withdraw"),
            Swap("swap"),
            Staking("staking"),
            Battery("battery"),
            Send("send"),
            Trade("trade"),
            Exchange("exchange"),
            Dapp("dapp"),
            Other("other")
        }

        /** banner_view */
        fun bannerView(bannerId: String)

        /** banner_click */
        fun bannerClick(bannerId: String, action: HomeBannerAction)
    }


    interface InappReview {

        enum class InappReviewAction(val key: String) {
            Manual("manual"),
            SentTransaction("sent_transaction")
        }

        /** inapp_review */
        fun inappReview(action: InappReviewAction?)
    }


    interface InstallApp {

        /** install_app */
        fun installApp(referrer: String?, deeplink: String?, installerStore: String?)
    }


    interface LaunchApp {

        enum class LaunchAppTheme(val key: String) {
            Dark("dark"),
            Light("light"),
            DeepBlue("deep_blue"),
            SystemLight("system_light"),
            SystemDark("system_dark")
        }

        enum class LaunchAppAppIcon(val key: String) {
            Default("default"),
            Accent("accent"),
            Dark("dark"),
            Light("light")
        }

        enum class LaunchAppPushPermission(val key: String) {
            Granted("granted"),
            Denied("denied"),
            NotRequested("not_requested"),
            Unsupported("unsupported")
        }

        /** launch_app */
        fun launchApp(
            theme: LaunchAppTheme?,
            appIcon: LaunchAppAppIcon?,
            walletsCount: Int?,
            pushPermission: LaunchAppPushPermission?,
            featureFlags: Map<String, Any> = emptyMap()
        )
    }


    interface Migration {

        enum class MigrationFrom(val key: String) {
            Settings("settings"),
            Onboarding("onboarding"),
            Setup("setup"),
            Story("story"),
            Banner("banner"),
            Raffle("raffle"),
            Deeplink("deeplink")
        }

        enum class MigrationFeeAsset(val key: String) {
            Coin("coin"),
            BatteryCharges("battery_charges"),
            BatteryTonInstantFee("battery_ton_instant_fee"),
            Gasless("gasless")
        }

        enum class MigrationFailedPart(val key: String) {
            Setup("setup"),
            TronUsdt("tron_usdt"),
            TronTrx("tron_trx"),
            Ton("ton")
        }

        enum class MigrationChain(val key: String) {
            Ton("ton"),
            Eth("eth"),
            Base("base"),
            Arb("arb"),
            Bnb("bnb"),
            Pol("pol"),
            Sol("sol"),
            Tron("tron"),
            Btc("btc"),
            Ltc("ltc"),
            Doge("doge"),
            Bch("bch"),
            Multichain("multichain")
        }

        /** migration_start */
        fun migrationStart(from: MigrationFrom)

        /** migrate_wallet_selection_view */
        fun migrateWalletSelectionView()

        /** migrate_wallet_selection_click */
        fun migrateWalletSelectionClick()

        /** migrate_transaction_confirmation_view */
        fun migrateTransactionConfirmationView(feeAsset: MigrationFeeAsset)

        /** migrate_transaction_success */
        fun migrateTransactionSuccess(feeAsset: MigrationFeeAsset)

        /** migrate_transaction_error */
        fun migrateTransactionError(
            feeAsset: MigrationFeeAsset,
            failedPart: MigrationFailedPart,
            isPartial: Boolean,
            errorType: String?,
            errorCode: Int?,
            errorMessage: String?
        )

        /** migrate_transaction_prepare_error */
        fun migrateTransactionPrepareError(
            chain: MigrationChain,
            isBlocking: Boolean,
            errorType: String?,
            errorCode: Int?,
            errorMessage: String?
        )
    }


    interface MysteryRaffle {

        enum class MysteryRaffleSource(val key: String) {
            WalletMain("wallet_main"),
            WalletsList("wallets_list"),
            Trade("trade"),
            SwapPromo("swap_promo"),
            DeepLink("deep_link")
        }

        enum class MysteryRaffleKind(val key: String) {
            Active("active"),
            Migration("migration"),
            Won("won"),
            Lost("lost")
        }

        enum class MysteryRaffleAction(val key: String) {
            Migrate("migrate"),
            Swap("swap"),
            ResultsLink("results_link")
        }

        /** raffle_banner_view */
        fun raffleBannerView(source: MysteryRaffleSource)

        /** raffle_banner_click */
        fun raffleBannerClick(source: MysteryRaffleSource)

        /** raffle_banner_dismiss */
        fun raffleBannerDismiss(source: MysteryRaffleSource)

        /** raffle_open */
        fun raffleOpen(
            source: MysteryRaffleSource,
            kind: MysteryRaffleKind,
            ticketsTotal: Int,
            prize: String?
        )

        /** raffle_click_cta */
        fun raffleClickCta(action: MysteryRaffleAction, kind: MysteryRaffleKind, ticketsTotal: Int)

        /** raffle_click_task */
        fun raffleClickTask(taskId: String)

        /** raffle_click_milestone */
        fun raffleClickMilestone(milestoneId: String)

        /** raffle_click_get_more */
        fun raffleClickGetMore(kind: MysteryRaffleKind, ticketsTotal: Int)
    }


    interface OnboardingFlow {

        /** onboarding_view_welcome */
        fun onboardingViewWelcome()

        /** onboarding_passcode_created */
        fun onboardingPasscodeCreated()

        /** onboarding_passcode_mismatch */
        fun onboardingPasscodeMismatch()

        /** onboarding_view_customize */
        fun onboardingViewCustomize()

        /** onboarding_click_customize_continue */
        fun onboardingClickCustomizeContinue()
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


    interface PasscodeLockout {

        enum class PasscodeLockoutFrom(val key: String) {
            Unlock("unlock"),
            Confirmation("confirmation"),
            Change("change")
        }

        /** passcode_lockout */
        fun passcodeLockout(from: PasscodeLockoutFrom, lockoutSeconds: Int, failedAttempts: Int)
    }


    interface PushClick {

        /** push_click */
        fun pushClick(pushId: String?, deepLink: String?)
    }


    interface RedOperations {

        enum class RedOperationsFlow(val key: String) {
            Transfer("transfer"),
            Swap("swap"),
            Stake("stake"),
            TonConnect("ton_connect")
        }

        enum class RedOperationsOperation(val key: String) {
            Emulate("emulate"),
            Send("send"),
            Quote("quote"),
            Stake("stake"),
            Unstake("unstake"),
            ConnectWallet("connect_wallet"),
            ConfirmTransaction("confirm_transaction")
        }

        enum class RedOperationsOutcome(val key: String) {
            Success("success"),
            Fail("fail"),
            Cancel("cancel")
        }

        /** op_attempt */
        fun opAttempt(
            operationId: String,
            flow: RedOperationsFlow,
            operation: RedOperationsOperation,
            attemptSource: String?,
            startedAtMs: Int,
            otherMetadata: String?
        )

        /** op_terminal */
        fun opTerminal(
            operationId: String,
            flow: RedOperationsFlow,
            operation: RedOperationsOperation,
            outcome: RedOperationsOutcome,
            durationMs: Double,
            finishedAtMs: Int,
            errorCode: Int?,
            errorMessage: String?,
            errorType: String?,
            stage: String?,
            otherMetadata: String?
        )
    }


    interface SendNative {

        enum class SendNativeFrom(val key: String) {
            WalletScreen("wallet_screen"),
            JettonScreen("jetton_screen"),
            DeepLink("deep_link"),
            TonconnectLocal("tonconnect_local"),
            TonconnectRemote("tonconnect_remote"),
            QrCode("qr_code"),
            Walletconnect("walletconnect")
        }

        enum class SendNativeFeeAsset(val key: String) {
            Coin("coin"),
            BatteryCharges("battery_charges"),
            BatteryTonInstantFee("battery_ton_instant_fee"),
            Gasless("gasless")
        }

        /** send_open */
        fun sendOpen(from: SendNativeFrom)

        /** send_click */
        fun sendClick(from: SendNativeFrom, asset: String, amount: Double)

        /** send_confirm */
        fun sendConfirm(
            from: SendNativeFrom,
            asset: String,
            amount: Double,
            feeAsset: SendNativeFeeAsset,
            appId: String?
        )

        /** send_success */
        fun sendSuccess(
            from: SendNativeFrom,
            asset: String,
            amount: Double,
            feeAsset: SendNativeFeeAsset,
            appId: String?
        )

        /** send_failed */
        fun sendFailed(
            from: SendNativeFrom,
            asset: String,
            amount: Double,
            feeAsset: SendNativeFeeAsset,
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


    interface Stories {

        enum class StoriesFrom(val key: String) {
            Wallet("wallet"),
            DeepLink("deep-link"),
            Updates("updates")
        }

        enum class StoriesButtonType(val key: String) {
            Deeplink("deeplink"),
            Link("link")
        }

        /** story_open */
        fun storyOpen(storyId: String, from: StoriesFrom)

        /** story_page_view */
        fun storyPageView(storyId: String)

        /** story_click */
        fun storyClick(
            storyId: String,
            buttonType: StoriesButtonType,
            buttonPayload: String,
            buttonTitle: String
        )
    }


    interface SwapsNative {

        enum class SwapsNativeType(val key: String) {
            Native("native")
        }

        enum class SwapsNativeWalletMode(val key: String) {
            Multi("multi"),
            Single("single")
        }

        enum class SwapsNativeFeeAsset(val key: String) {
            Coin("coin"),
            BatteryCharges("battery_charges"),
            BatteryTonInstantFee("battery_ton_instant_fee"),
            Gasless("gasless")
        }

        /** swap_open */
        fun swapOpen(type: SwapsNativeType, walletMode: SwapsNativeWalletMode)

        /** swap_click */
        fun swapClick(
            type: SwapsNativeType,
            walletMode: SwapsNativeWalletMode,
            assetFrom: String,
            assetTo: String,
            isMax: Boolean?
        )

        /** swap_confirm */
        fun swapConfirm(
            type: SwapsNativeType,
            walletMode: SwapsNativeWalletMode,
            assetFrom: String,
            assetTo: String,
            feeAsset: SwapsNativeFeeAsset,
            providerName: String,
            isMax: Boolean?
        )

        /** swap_failed */
        fun swapFailed(
            type: SwapsNativeType,
            walletMode: SwapsNativeWalletMode,
            assetFrom: String,
            assetTo: String,
            feeAsset: SwapsNativeFeeAsset,
            providerName: String,
            errorMessage: String,
            isMax: Boolean?
        )

        /** swap_success */
        fun swapSuccess(
            type: SwapsNativeType,
            walletMode: SwapsNativeWalletMode,
            assetFrom: String,
            assetTo: String,
            feeAsset: SwapsNativeFeeAsset,
            providerName: String,
            isMax: Boolean?
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


    interface TradeUiFlow {

        enum class TradeStartedFrom(val key: String) {
            WalletScreen("wallet_screen"),
            JettonScreen("jetton_screen"),
            TabBar("tab_bar"),
            DeepLink("deep_link"),
            QrCode("qr_code")
        }

        enum class TradeFavoriteAddFrom(val key: String) {
            AssetDetails("asset_details")
        }

        enum class TradeFavoriteRemoveFrom(val key: String) {
            AssetDetails("asset_details"),
            FavoritesSection("favorites_section")
        }

        /** trade_started */
        fun tradeStarted(from: TradeStartedFrom)

        /** trade_click_asset */
        fun tradeClickAsset(from: TradeStartedFrom, asset: String)

        /** trade_search */
        fun tradeSearch(from: TradeStartedFrom, query: String?)

        /** trade_search_click */
        fun tradeSearchClick(from: TradeStartedFrom, query: String?, asset: String)

        /** trade_favorite_add */
        fun tradeFavoriteAdd(from: TradeFavoriteAddFrom, asset: String)

        /** trade_favorite_remove */
        fun tradeFavoriteRemove(from: TradeFavoriteRemoveFrom, asset: String)

        /** trade_favorite_click */
        fun tradeFavoriteClick(asset: String, position: Int)
    }


    interface TransactionSent {

        enum class TransactionSentCategory(val key: String) {
            Transfer("transfer"),
            Swap("swap"),
            Call("call"),
            Staking("staking")
        }

        enum class TransactionSentCategoryDetail(val key: String) {
            Coin("coin"),
            Token("token"),
            Nft("nft"),
            Onchain("onchain"),
            CrossChain("cross_chain"),
            DomainRenew("domain_renew"),
            Subscription("subscription"),
            Multisig("multisig"),
            Deploy("deploy"),
            Unknown("unknown"),
            Stake("stake"),
            Unstake("unstake"),
            Claim("claim"),
            Restake("restake"),
            Compound("compound")
        }

        enum class TransactionSentFeeAsset(val key: String) {
            Coin("coin"),
            BatteryCharges("battery_charges"),
            BatteryTonInstantFee("battery_ton_instant_fee"),
            Gasless("gasless")
        }

        enum class TransactionSentWalletInterface(val key: String) {
            V1R1("v1R1"),
            V1R2("v1R2"),
            V1R3("v1R3"),
            V2R1("v2R1"),
            V2R2("v2R2"),
            V3R1("v3R1"),
            V3R2("v3R2"),
            V4R1("v4R1"),
            V4R2("v4R2"),
            V5Beta("v5Beta"),
            V5R1("v5R1"),
            Legacy("legacy"),
            P2sh("p2sh"),
            Segwit("segwit"),
            Taproot("taproot"),
            Eoa("eoa"),
            SmartAccount("smart_account")
        }

        enum class TransactionSentWalletSource(val key: String) {
            Mnemonic("mnemonic"),
            Ledger("ledger"),
            Signer("signer"),
            Keystone("keystone"),
            Privatekey("privatekey"),
            Watchonly("watchonly")
        }

        enum class TransactionSentWalletMode(val key: String) {
            Multi("multi"),
            Single("single")
        }

        enum class TransactionSentInitiatedBy(val key: String) {
            User("user"),
            DeepLink("deep_link"),
            QrCode("qr_code"),
            TonconnectLocal("tonconnect_local"),
            TonconnectRemote("tonconnect_remote"),
            Walletconnect("walletconnect"),
            EvmInjected("evm_injected")
        }

        /** transaction_sent */
        fun transactionSent(
            category: TransactionSentCategory,
            categoryDetail: TransactionSentCategoryDetail,
            asset: String,
            amount: Double,
            feeAsset: TransactionSentFeeAsset,
            walletInterface: TransactionSentWalletInterface,
            walletSource: TransactionSentWalletSource,
            walletMode: TransactionSentWalletMode,
            initiatedBy: TransactionSentInitiatedBy,
            appId: String?,
            dappUrl: String?,
            isMax: Boolean?,
            toAsset: String?,
            stakingProvider: String?,
            isLiquid: Boolean?
        )
    }


    interface TwaSunset {

        enum class TwaSunsetTelegramPlatform(val key: String) {
            Ios("ios"),
            Android("android"),
            AndroidX("android_x"),
            Web("web"),
            Other("other")
        }

        enum class TwaSunsetDestination(val key: String) {
            AppStore("app_store"),
            GooglePlay("google_play"),
            Web("web")
        }

        /** twa_sunset_open */
        fun twaSunsetOpen(
            telegramPlatform: TwaSunsetTelegramPlatform?,
            hasWallets: Boolean?,
            walletsCount: Int?
        )

        /** twa_sunset_download_click */
        fun twaSunsetDownloadClick(destination: TwaSunsetDestination)

        /** twa_sunset_reveal_start */
        fun twaSunsetRevealStart()

        /** twa_sunset_reveal_success */
        fun twaSunsetRevealSuccess()

        /** twa_sunset_sign_out */
        fun twaSunsetSignOut()
    }


    interface UserErrors {

        enum class UserErrorsSeverity(val key: String) {
            Warning("warning"),
            Error("error"),
            Fatal("fatal")
        }

        /** custom_error */
        fun customError(
            severity: UserErrorsSeverity,
            errorMessage: String,
            errorCode: String?,
            otherMetadata: String?
        )
    }


    interface WalletFlow {

        enum class WalletFlowFrom(val key: String) {
            Onboarding("onboarding"),
            Main("main")
        }

        enum class WalletFlowWalletMode(val key: String) {
            Multi("multi"),
            Single("single")
        }

        enum class WalletFlowSource(val key: String) {
            Onboarding("onboarding"),
            WalletSetupSection("wallet_setup_section"),
            Settings("settings")
        }

        enum class WalletFlowWalletSource(val key: String) {
            Mnemonic("mnemonic"),
            Ledger("ledger"),
            Signer("signer"),
            Keystone("keystone"),
            Privatekey("privatekey"),
            Watchonly("watchonly")
        }

        /** add_wallet_menu_view */
        fun addWalletMenuView(from: WalletFlowFrom)

        /** wallet_create_started */
        fun walletCreateStarted(walletMode: WalletFlowWalletMode, from: WalletFlowFrom)

        /** wallet_backup_started */
        fun walletBackupStarted(walletMode: WalletFlowWalletMode, source: WalletFlowSource)

        /** wallet_backup_skip */
        fun walletBackupSkip(walletMode: WalletFlowWalletMode, source: WalletFlowSource)

        /** wallet_backup_success */
        fun walletBackupSuccess(walletMode: WalletFlowWalletMode, source: WalletFlowSource)

        /** wallet_backup_error */
        fun walletBackupError(
            walletMode: WalletFlowWalletMode,
            source: WalletFlowSource,
            errorType: String?,
            errorCode: Int?,
            errorMessage: String?
        )

        /** wallet_create_success */
        fun walletCreateSuccess(
            walletMode: WalletFlowWalletMode,
            backedUp: Boolean,
            from: WalletFlowFrom
        )

        /** wallet_import_started */
        fun walletImportStarted(
            walletMode: WalletFlowWalletMode,
            walletSource: WalletFlowWalletSource,
            from: WalletFlowFrom
        )

        /** wallet_import_error */
        fun walletImportError(
            walletMode: WalletFlowWalletMode,
            walletSource: WalletFlowWalletSource,
            from: WalletFlowFrom,
            errorType: String?,
            errorCode: Int?,
            errorMessage: String?
        )

        /** wallet_import_success */
        fun walletImportSuccess(
            walletMode: WalletFlowWalletMode,
            walletSource: WalletFlowWalletSource,
            from: WalletFlowFrom
        )
    }


    interface WalletOpen {

        enum class WalletOpenWalletMode(val key: String) {
            Multi("multi"),
            Single("single")
        }

        enum class WalletOpenWalletSource(val key: String) {
            Mnemonic("mnemonic"),
            Ledger("ledger"),
            Signer("signer"),
            Keystone("keystone"),
            Privatekey("privatekey"),
            Watchonly("watchonly")
        }

        enum class WalletOpenWalletInterface(val key: String) {
            V1R1("v1R1"),
            V1R2("v1R2"),
            V1R3("v1R3"),
            V2R1("v2R1"),
            V2R2("v2R2"),
            V3R1("v3R1"),
            V3R2("v3R2"),
            V4R1("v4R1"),
            V4R2("v4R2"),
            V5Beta("v5Beta"),
            V5R1("v5R1"),
            Legacy("legacy"),
            P2sh("p2sh"),
            Segwit("segwit"),
            Taproot("taproot"),
            Eoa("eoa"),
            SmartAccount("smart_account")
        }

        /** wallet_open */
        fun walletOpen(
            walletMode: WalletOpenWalletMode,
            walletSource: WalletOpenWalletSource,
            walletInterface: WalletOpenWalletInterface?
        )
    }


    interface WithdrawFlow {

        enum class WithdrawFlowFrom(val key: String) {
            WalletScreen("wallet_screen"),
            JettonScreen("jetton_screen"),
            DeepLink("deep_link"),
            QrCode("qr_code")
        }

        enum class WithdrawFlowWithdrawOption(val key: String) {
            SendTokens("send_tokens"),
            SellToCard("sell_to_card"),
            GetUsdtOtherNetworks("get_usdt_other_networks")
        }

        enum class WithdrawFlowFeeAsset(val key: String) {
            Coin("coin"),
            BatteryCharges("battery_charges"),
            BatteryTonInstantFee("battery_ton_instant_fee"),
            Gasless("gasless")
        }

        /** withdraw_started */
        fun withdrawStarted(from: WithdrawFlowFrom, availableOptions: String)

        /** withdraw_option_click */
        fun withdrawOptionClick(from: WithdrawFlowFrom, withdrawOption: WithdrawFlowWithdrawOption)

        /** withdraw_view_fiat_choose_asset */
        fun withdrawViewFiatChooseAsset(
            from: WithdrawFlowFrom,
            withdrawOption: WithdrawFlowWithdrawOption,
            availableOptions: String
        )

        /** withdraw_click_fiat_asset */
        fun withdrawClickFiatAsset(
            from: WithdrawFlowFrom,
            withdrawOption: WithdrawFlowWithdrawOption,
            sellAsset: String
        )

        /** withdraw_view_fiat_payment_method */
        fun withdrawViewFiatPaymentMethod(
            from: WithdrawFlowFrom,
            withdrawOption: WithdrawFlowWithdrawOption,
            sellAsset: String,
            buyAsset: String,
            availableOptions: String
        )

        /** withdraw_click_fiat_payment_method */
        fun withdrawClickFiatPaymentMethod(
            from: WithdrawFlowFrom,
            withdrawOption: WithdrawFlowWithdrawOption,
            sellAsset: String,
            buyAsset: String,
            paymentMethod: String
        )

        /** withdraw_view_ramp_insert_amount */
        fun withdrawViewRampInsertAmount(
            from: WithdrawFlowFrom,
            withdrawOption: WithdrawFlowWithdrawOption,
            sellAsset: String,
            buyAsset: String,
            paymentMethod: String,
            providerName: String
        )

        /** withdraw_click_ramp_insert_amount_continue */
        fun withdrawClickRampInsertAmountContinue(
            from: WithdrawFlowFrom,
            withdrawOption: WithdrawFlowWithdrawOption,
            sellAsset: String,
            buyAsset: String,
            paymentMethod: String,
            providerName: String,
            amount: Double
        )

        /** withdraw_view_ramp_alert */
        fun withdrawViewRampAlert(
            from: WithdrawFlowFrom,
            withdrawOption: WithdrawFlowWithdrawOption,
            sellAsset: String,
            buyAsset: String,
            paymentMethod: String,
            providerName: String,
            amount: Double
        )

        /** withdraw_continue_to_ramp_provider */
        fun withdrawContinueToRampProvider(
            from: WithdrawFlowFrom,
            withdrawOption: WithdrawFlowWithdrawOption,
            sellAsset: String,
            buyAsset: String,
            paymentMethod: String,
            providerName: String,
            amount: Double,
            txId: String
        )

        /** withdraw_view_choose_asset */
        fun withdrawViewChooseAsset(
            from: WithdrawFlowFrom,
            withdrawOption: WithdrawFlowWithdrawOption,
            availableOptions: String
        )

        /** withdraw_click_asset */
        fun withdrawClickAsset(
            from: WithdrawFlowFrom,
            withdrawOption: WithdrawFlowWithdrawOption,
            sellAsset: String
        )

        /** withdraw_view_choose_stablecoin */
        fun withdrawViewChooseStablecoin(
            from: WithdrawFlowFrom,
            withdrawOption: WithdrawFlowWithdrawOption,
            sellAsset: String,
            availableOptions: String
        )

        /** withdraw_click_stablecoin */
        fun withdrawClickStablecoin(
            from: WithdrawFlowFrom,
            withdrawOption: WithdrawFlowWithdrawOption,
            sellAsset: String,
            stablecoinSymbol: String
        )

        /** withdraw_view_choose_network */
        fun withdrawViewChooseNetwork(
            from: WithdrawFlowFrom,
            withdrawOption: WithdrawFlowWithdrawOption,
            sellAsset: String,
            stablecoinSymbol: String,
            availableOptions: String
        )

        /** withdraw_click_network */
        fun withdrawClickNetwork(
            from: WithdrawFlowFrom,
            withdrawOption: WithdrawFlowWithdrawOption,
            sellAsset: String,
            stablecoinSymbol: String,
            buyAsset: String
        )

        /** withdraw_view_insert_amount */
        fun withdrawViewInsertAmount(
            from: WithdrawFlowFrom,
            withdrawOption: WithdrawFlowWithdrawOption,
            sellAsset: String,
            stablecoinSymbol: String,
            buyAsset: String
        )

        /** withdraw_click_insert_amount_continue */
        fun withdrawClickInsertAmountContinue(
            from: WithdrawFlowFrom,
            withdrawOption: WithdrawFlowWithdrawOption,
            sellAsset: String,
            stablecoinSymbol: String,
            buyAsset: String,
            amount: Double
        )

        /** withdraw_send_confirm */
        fun withdrawSendConfirm(
            from: WithdrawFlowFrom,
            withdrawOption: WithdrawFlowWithdrawOption,
            sellAsset: String,
            stablecoinSymbol: String,
            buyAsset: String,
            amount: Double,
            feeAsset: WithdrawFlowFeeAsset
        )

        /** withdraw_send_success */
        fun withdrawSendSuccess(
            from: WithdrawFlowFrom,
            withdrawOption: WithdrawFlowWithdrawOption,
            sellAsset: String,
            stablecoinSymbol: String,
            buyAsset: String,
            amount: Double,
            feeAsset: WithdrawFlowFeeAsset
        )
    }
}
