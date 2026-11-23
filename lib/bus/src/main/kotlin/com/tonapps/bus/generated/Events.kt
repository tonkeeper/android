package com.tonapps.bus.generated

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
interface Events {

    companion object {
        const val VERSION = "6.1.2"
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


    interface Banner {

        enum class BannerWalletMode(val key: String) {
            Multi("multi"),
            Single("single")
        }

        enum class BannerFrom(val key: String) {
            Home("home"),
            Browser("browser")
        }

        enum class BannerAction(val key: String) {
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
        fun bannerView(bannerId: String, walletMode: BannerWalletMode, from: BannerFrom)

        /** banner_click */
        fun bannerClick(
            bannerId: String,
            action: BannerAction,
            walletMode: BannerWalletMode,
            from: BannerFrom
        )
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
            Story("story"),
            ConnectedApps("connected_apps"),
            Other("other")
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
            Bsc("bsc"),
            Polygon("polygon"),
            Avax("avax"),
            Robinhood("robinhood"),
            Sol("sol"),
            Tron("tron"),
            Btc("btc"),
            Ltc("ltc"),
            Doge("doge"),
            Bch("bch"),
            Lighter("lighter"),
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
            DeepLink("deep_link"),
            History("history"),
            Collectibles("collectibles"),
            Tonconnect("tonconnect"),
            EarnFeatured("earn_featured"),
            EarnYourVaults("earn_your_vaults"),
            EarnApy("earn_apy"),
            Dapp("dapp"),
            Shortcut("shortcut"),
            Other("other")
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
            location: String,
            loadDurationMs: Long
        )

        /** dapp_app_load_failed */
        fun dappAppLoadFailed(
            from: DappAppClickFrom,
            url: String,
            assetChain: DappBrowserAssetChain,
            appId: String,
            bannerId: String?,
            location: String,
            errorCode: String,
            httpStatus: Int?,
            loadDurationMs: Long
        )

        /** dapp_browser_search_open */
        fun dappBrowserSearchOpen(url: String, location: String)

        /** dapp_browser_search_click */
        fun dappBrowserSearchClick(url: String, location: String)
    }


    interface DeeplinkOpen {

        enum class DeeplinkOpenFrom(val key: String) {
            AppLink("app_link"),
            UniversalLink("universal_link"),
            DeferredLink("deferred_link")
        }

        enum class DeeplinkOpenLinkType(val key: String) {
            Transfer("transfer"),
            Staking("staking"),
            Pool("pool"),
            Swap("swap"),
            Deposit("deposit"),
            Withdraw("withdraw"),
            Action("action"),
            Publish("publish"),
            ExternalSign("external_sign"),
            Tonconnect("tonconnect"),
            WalletConnect("wallet_connect"),
            Dapp("dapp"),
            Battery("battery"),
            Browser("browser"),
            Migration("migration"),
            Trading("trading"),
            TradeAsset("trade_asset"),
            Story("story"),
            Receive("receive"),
            Backup("backup"),
            AddWallet("add_wallet"),
            Main("main"),
            Raffle("raffle"),
            Perps("perps"),
            Unknown("unknown")
        }

        /** deeplink_open */
        fun deeplinkOpen(
            from: DeeplinkOpenFrom,
            linkType: DeeplinkOpenLinkType,
            isColdStart: Boolean
        )
    }


    interface DepositFlow {

        enum class DepositFlowFrom(val key: String) {
            WalletScreen("wallet_screen"),
            JettonScreen("jetton_screen"),
            DeepLink("deep_link"),
            QrCode("qr_code"),
            HistoryScreen("history_screen"),
            Browser("browser"),
            Collectibles("collectibles"),
            Perps("perps"),
            Walletconnect("walletconnect"),
            Tonconnect("tonconnect"),
            Staking("staking"),
            Migration("migration"),
            Other("other")
        }

        enum class DepositFlowWalletMode(val key: String) {
            Multi("multi"),
            Single("single")
        }

        enum class DepositFlowDepositScreen(val key: String) {
            AddFunds("add_funds"),
            BuyCrypto("buy_crypto")
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

        enum class DepositFlowProviderMode(val key: String) {
            Headless("headless"),
            Merchant("merchant")
        }

        enum class DepositFlowOutcome(val key: String) {
            Success("success"),
            Failed("failed"),
            Pending("pending"),
            Unknown("unknown"),
            Cancelled("cancelled")
        }

        enum class DepositFlowFailureReason(val key: String) {
            Declined("declined"),
            LimitReached("limit_reached"),
            QuoteRejected("quote_rejected"),
            NotConfirmed("not_confirmed"),
            Unavailable("unavailable"),
            VerificationPending("verification_pending"),
            VerificationUnavailable("verification_unavailable"),
            NoResponse("no_response"),
            TransactionCancelled("transaction_cancelled"),
            Unknown("unknown")
        }

        /** deposit_started */
        fun depositStarted(
            from: DepositFlowFrom,
            walletMode: DepositFlowWalletMode,
            depositScreen: DepositFlowDepositScreen?,
            availableOptions: String
        )

        /** deposit_option_click */
        fun depositOptionClick(
            from: DepositFlowFrom,
            walletMode: DepositFlowWalletMode,
            addFundsOption: DepositFlowAddFundsOption
        )

        /** deposit_view_receive_tokens */
        fun depositViewReceiveTokens(
            from: DepositFlowFrom,
            walletMode: DepositFlowWalletMode,
            addFundsOption: DepositFlowAddFundsOption,
            depositScreen: DepositFlowDepositScreen?,
            network: DepositFlowNetwork
        )

        /** deposit_view_p2p_alert */
        fun depositViewP2pAlert(from: DepositFlowFrom, addFundsOption: DepositFlowAddFundsOption)

        /** deposit_continue_to_p2p_market */
        fun depositContinueToP2pMarket(
            from: DepositFlowFrom,
            walletMode: DepositFlowWalletMode,
            addFundsOption: DepositFlowAddFundsOption,
            depositScreen: DepositFlowDepositScreen?
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
            walletMode: DepositFlowWalletMode,
            addFundsOption: DepositFlowAddFundsOption,
            sellAsset: String,
            buyAsset: String
        )

        /** deposit_view_qr_code */
        fun depositViewQrCode(
            from: DepositFlowFrom,
            walletMode: DepositFlowWalletMode,
            addFundsOption: DepositFlowAddFundsOption,
            sellAsset: String,
            buyAsset: String
        )

        /** deposit_view_fiat_choose_asset */
        fun depositViewFiatChooseAsset(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            depositScreen: DepositFlowDepositScreen?,
            availableOptions: String
        )

        /** deposit_click_fiat_asset */
        fun depositClickFiatAsset(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            depositScreen: DepositFlowDepositScreen?,
            buyAsset: String
        )

        /** deposit_view_fiat_payment_method */
        fun depositViewFiatPaymentMethod(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            depositScreen: DepositFlowDepositScreen?,
            buyAsset: String,
            sellAsset: String,
            availableOptions: String
        )

        /** deposit_click_fiat_payment_method */
        fun depositClickFiatPaymentMethod(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            depositScreen: DepositFlowDepositScreen?,
            buyAsset: String,
            sellAsset: String,
            paymentMethod: String
        )

        /** deposit_view_ramp_insert_amount */
        fun depositViewRampInsertAmount(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            depositScreen: DepositFlowDepositScreen?,
            buyAsset: String,
            sellAsset: String,
            paymentMethod: String,
            providerName: String,
            providerMode: DepositFlowProviderMode?
        )

        /** deposit_click_ramp_insert_amount_continue */
        fun depositClickRampInsertAmountContinue(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            depositScreen: DepositFlowDepositScreen?,
            buyAsset: String,
            sellAsset: String,
            paymentMethod: String,
            providerName: String,
            providerMode: DepositFlowProviderMode?
        )

        /** deposit_view_ramp_alert */
        fun depositViewRampAlert(
            from: DepositFlowFrom,
            addFundsOption: DepositFlowAddFundsOption,
            buyAsset: String,
            sellAsset: String,
            paymentMethod: String,
            providerName: String
        )

        /** deposit_continue_to_ramp_provider */
        fun depositContinueToRampProvider(
            from: DepositFlowFrom,
            walletMode: DepositFlowWalletMode,
            addFundsOption: DepositFlowAddFundsOption,
            depositScreen: DepositFlowDepositScreen?,
            buyAsset: String,
            sellAsset: String,
            paymentMethod: String,
            providerName: String,
            providerMode: DepositFlowProviderMode?,
            txId: String
        )

        /** deposit_ramp_result */
        fun depositRampResult(
            from: DepositFlowFrom,
            walletMode: DepositFlowWalletMode,
            addFundsOption: DepositFlowAddFundsOption,
            depositScreen: DepositFlowDepositScreen?,
            buyAsset: String,
            sellAsset: String,
            paymentMethod: String,
            providerName: String,
            providerMode: DepositFlowProviderMode?,
            txId: String,
            outcome: DepositFlowOutcome,
            failureReason: DepositFlowFailureReason?
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


    interface DomainMigration {

        enum class DomainMigrationCheckOutcome(val key: String) {
            Sent("sent"),
            Unreachable("unreachable"),
            Failed("failed")
        }

        enum class DomainMigrationAnswer(val key: String) {
            Found("found"),
            None("none")
        }

        enum class DomainMigrationFrom(val key: String) {
            Check("check"),
            OldDomain("old_domain"),
            Manual("manual")
        }

        enum class DomainMigrationOfferClickAction(val key: String) {
            Continue("continue"),
            NotNow("not_now")
        }

        enum class DomainMigrationPopupResultOutcome(val key: String) {
            Approved("approved"),
            None("none"),
            Declined("declined"),
            Closed("closed"),
            Blocked("blocked"),
            Rejected("rejected")
        }

        enum class DomainMigrationConfirmClickAction(val key: String) {
            Add("add"),
            NotMine("not_mine")
        }

        enum class DomainMigrationNotMineClickAction(val key: String) {
            MoveManually("move_manually"),
            ContinueWithout("continue_without")
        }

        enum class DomainMigrationImportResultOutcome(val key: String) {
            Success("success"),
            AlreadyHere("already_here"),
            Failed("failed")
        }

        /** domain_migration_check */
        fun domainMigrationCheck(outcome: DomainMigrationCheckOutcome)

        /** domain_migration_check_answer */
        fun domainMigrationCheckAnswer(answer: DomainMigrationAnswer)

        /** domain_migration_offer_view */
        fun domainMigrationOfferView(from: DomainMigrationFrom)

        /** domain_migration_offer_click */
        fun domainMigrationOfferClick(action: DomainMigrationOfferClickAction)

        /** domain_migration_popup_result */
        fun domainMigrationPopupResult(outcome: DomainMigrationPopupResultOutcome)

        /** domain_migration_confirm_view */
        fun domainMigrationConfirmView(accountsCount: Int, alreadyHereAccountsCount: Int)

        /** domain_migration_confirm_click */
        fun domainMigrationConfirmClick(action: DomainMigrationConfirmClickAction)

        /** domain_migration_not_mine_click */
        fun domainMigrationNotMineClick(action: DomainMigrationNotMineClickAction)

        /** domain_migration_import_result */
        fun domainMigrationImportResult(
            outcome: DomainMigrationImportResultOutcome,
            accountsCount: Int,
            legacyUserId: String?
        )
    }


    interface EarnFlowExternal {

        /** earn_webview_screen_view */
        fun earnWebviewScreenView(isExternalWebView: Int, eventProvider: String, screenName: String)

        /** earn_vault_list_viewed */
        fun earnVaultListViewed(
            isExternalWebView: Int,
            eventProvider: String,
            vaultsCount: Int,
            chainsShown: String,
            assetsShown: String
        )

        /** earn_vault_filter_applied */
        fun earnVaultFilterApplied(
            isExternalWebView: Int,
            eventProvider: String,
            filterType: String
        )

        /** earn_vault_sort_applied */
        fun earnVaultSortApplied(isExternalWebView: Int, eventProvider: String, sortBy: String)

        /** earn_vault_selected */
        fun earnVaultSelected(
            isExternalWebView: Int,
            eventProvider: String,
            vaultId: String,
            isInitialRoute: Boolean,
            asset: String,
            apy: Double,
            tvlUsd: Double,
            riskScore: Double?
        )

        /** earn_deposit_started */
        fun earnDepositStarted(isExternalWebView: Int, eventProvider: String, vaultId: String)

        /** earn_deposit_amount_entered */
        fun earnDepositAmountEntered(
            isExternalWebView: Int,
            eventProvider: String,
            vaultId: String,
            amountUsd: Double,
            isMax: Boolean
        )

        /** earn_deposit_review_shown */
        fun earnDepositReviewShown(
            isExternalWebView: Int,
            eventProvider: String,
            vaultId: String,
            estimatedGasUsd: Double,
            estimatedApy: Double
        )

        /** earn_deposit_confirm_tapped */
        fun earnDepositConfirmTapped(isExternalWebView: Int, eventProvider: String, vaultId: String)

        /** earn_deposit_success */
        fun earnDepositSuccess(
            isExternalWebView: Int,
            eventProvider: String,
            vaultId: String,
            amountUsd: Double
        )

        /** earn_deposit_failed */
        fun earnDepositFailed(
            isExternalWebView: Int,
            eventProvider: String,
            vaultId: String,
            errorCode: String,
            errorMessage: String?
        )

        /** earn_withdraw_started */
        fun earnWithdrawStarted(isExternalWebView: Int, eventProvider: String, vaultId: String)

        /** earn_withdraw_amount_entered */
        fun earnWithdrawAmountEntered(
            isExternalWebView: Int,
            eventProvider: String,
            vaultId: String,
            amountUsd: Double,
            isMax: Boolean
        )

        /** earn_withdraw_review_shown */
        fun earnWithdrawReviewShown(
            isExternalWebView: Int,
            eventProvider: String,
            vaultId: String,
            estimatedGasUsd: Double
        )

        /** earn_withdraw_confirm_tapped */
        fun earnWithdrawConfirmTapped(
            isExternalWebView: Int,
            eventProvider: String,
            vaultId: String
        )

        /** earn_withdraw_requested */
        fun earnWithdrawRequested(
            isExternalWebView: Int,
            eventProvider: String,
            vaultId: String,
            amountUsd: Double
        )

        /** earn_withdraw_success */
        fun earnWithdrawSuccess(
            isExternalWebView: Int,
            eventProvider: String,
            vaultId: String,
            amountUsd: Double
        )

        /** earn_withdraw_failed */
        fun earnWithdrawFailed(
            isExternalWebView: Int,
            eventProvider: String,
            vaultId: String,
            errorCode: String,
            errorMessage: String?
        )

        /** earn_claim_started */
        fun earnClaimStarted(isExternalWebView: Int, eventProvider: String, vaultId: String)

        /** earn_claim_success */
        fun earnClaimSuccess(
            isExternalWebView: Int,
            eventProvider: String,
            vaultId: String,
            rewardAmountUsd: Double
        )

        /** earn_claim_failed */
        fun earnClaimFailed(
            isExternalWebView: Int,
            eventProvider: String,
            vaultId: String,
            errorCode: String,
            errorMessage: String?
        )

        /** earn_position_viewed */
        fun earnPositionViewed(
            isExternalWebView: Int,
            eventProvider: String,
            vaultId: String,
            balanceUsd: Double,
            accruedYieldUsd: Double
        )

        /** earn_history_viewed */
        fun earnHistoryViewed(isExternalWebView: Int, eventProvider: String)

        /** earn_external_link_clicked */
        fun earnExternalLinkClicked(isExternalWebView: Int, eventProvider: String, url: String)

        /** earn_webview_error */
        fun earnWebviewError(
            isExternalWebView: Int,
            eventProvider: String,
            errorType: String,
            errorMessage: String?
        )
    }


    interface EarnFlow {

        enum class EarnEntryViewFrom(val key: String) {
            EarnFeatured("earn_featured"),
            EarnYourVaults("earn_your_vaults"),
            EarnApy("earn_apy")
        }

        enum class EarnSignRequestFrom(val key: String) {
            Banner("banner"),
            Browser("browser"),
            BrowserSearch("browser_search"),
            BrowserConnected("browser_connected"),
            Push("push"),
            Sidebar("sidebar"),
            DeepLink("deep_link"),
            History("history"),
            Collectibles("collectibles"),
            Tonconnect("tonconnect"),
            EarnFeatured("earn_featured"),
            EarnYourVaults("earn_your_vaults"),
            EarnApy("earn_apy"),
            Dapp("dapp"),
            Shortcut("shortcut"),
            Other("other")
        }

        enum class EarnFlowMethod(val key: String) {
            SendTransaction("send_transaction"),
            PersonalSign("personal_sign"),
            SignTypedData("sign_typed_data")
        }

        enum class EarnFlowChain(val key: String) {
            Ton("ton"),
            Eth("eth"),
            Base("base"),
            Arb("arb"),
            Bsc("bsc"),
            Polygon("polygon"),
            Avax("avax"),
            Robinhood("robinhood"),
            Sol("sol"),
            Tron("tron"),
            Btc("btc"),
            Ltc("ltc"),
            Doge("doge"),
            Bch("bch"),
            Lighter("lighter"),
            Multichain("multichain")
        }

        enum class EarnFlowExitMethod(val key: String) {
            Page("page"),
            CloseButton("close_button"),
            SystemBack("system_back"),
            Other("other")
        }

        /** earn_entry_view */
        fun earnEntryView(
            from: EarnEntryViewFrom,
            vaultsCount: Int?,
            balanceUsd: Double?,
            asset: String?
        )

        /** earn_sign_request */
        fun earnSignRequest(
            from: EarnSignRequestFrom,
            method: EarnFlowMethod,
            chain: EarnFlowChain,
            vaultId: String?
        )

        /** earn_sign_success */
        fun earnSignSuccess(
            from: EarnSignRequestFrom,
            method: EarnFlowMethod,
            chain: EarnFlowChain,
            vaultId: String?
        )

        /** earn_sign_rejected */
        fun earnSignRejected(
            from: EarnSignRequestFrom,
            method: EarnFlowMethod,
            chain: EarnFlowChain,
            vaultId: String?
        )

        /** earn_sign_failed */
        fun earnSignFailed(
            from: EarnSignRequestFrom,
            method: EarnFlowMethod,
            chain: EarnFlowChain,
            vaultId: String?,
            errorCode: String,
            errorMessage: String?
        )

        /** earn_close */
        fun earnClose(from: EarnSignRequestFrom, exitMethod: EarnFlowExitMethod, durationMs: Long)
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

        enum class InstallAppAttributionStatus(val key: String) {
            Organic("organic"),
            NonOrganic("non_organic")
        }

        enum class InstallAppProvider(val key: String) {
            Appsflyer("appsflyer"),
            Branch("branch"),
            Linkrunner("linkrunner")
        }

        /** install_app */
        fun installApp(referrer: String?, deeplink: String?, installerStore: String?)

        /** install_attribution */
        fun installAttribution(
            attributionStatus: InstallAppAttributionStatus?,
            provider: InstallAppProvider,
            campaignId: String?,
            campaignName: String?
        )
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
            pushMismatchWalletsCount: Int?,
            featureFlags: Map<String, Any> = emptyMap()
        )
    }


    interface LedgerFlow {

        enum class LedgerFlowPurpose(val key: String) {
            Connect("connect"),
            ImportAccounts("import_accounts"),
            AddAccounts("add_accounts"),
            VerifyAddress("verify_address"),
            SignTransaction("sign_transaction"),
            SignMessage("sign_message"),
            SignProof("sign_proof"),
            CreateWallet("create_wallet")
        }

        enum class LedgerFlowOutcome(val key: String) {
            Success("success"),
            Rejected("rejected"),
            Failed("failed"),
            Cancelled("cancelled")
        }

        enum class LedgerFlowFailureType(val key: String) {
            BluetoothUnavailable("bluetooth_unavailable"),
            BluetoothOff("bluetooth_off"),
            NoPermission("no_permission"),
            DeviceNotFound("device_not_found"),
            UsbNotConnected("usb_not_connected"),
            UsbAccessDenied("usb_access_denied"),
            ScanUnavailable("scan_unavailable"),
            PairingRefused("pairing_refused"),
            PairingLost("pairing_lost"),
            UnsupportedDevice("unsupported_device"),
            ConnectionLost("connection_lost"),
            Busy("busy"),
            DeviceLocked("device_locked"),
            RequestExpired("request_expired"),
            Timeout("timeout"),
            AppNotInstalled("app_not_installed"),
            WrongAppOpen("wrong_app_open"),
            UserRefused("user_refused"),
            SettingRequired("setting_required"),
            AppUpdateRequired("app_update_required"),
            InvalidPayload("invalid_payload"),
            AddressMismatch("address_mismatch"),
            Unsupported("unsupported"),
            DeviceError("device_error"),
            Unexpected("unexpected")
        }

        enum class LedgerFlowLedgerApp(val key: String) {
            Ton("ton"),
            Ethereum("ethereum"),
            Tron("tron"),
            Bitcoin("bitcoin"),
            Solana("solana")
        }

        enum class LedgerFlowLedgerSetting(val key: String) {
            BlindSigning("blind_signing"),
            RawMessages("raw_messages"),
            DataAllowed("data_allowed"),
            CustomContracts("custom_contracts"),
            SignByHash("sign_by_hash")
        }

        enum class LedgerFlowLedgerModel(val key: String) {
            NanoS("nano_s"),
            NanoSPlus("nano_s_plus"),
            NanoX("nano_x"),
            Stax("stax"),
            Flex("flex"),
            NanoGen5("nano_gen5"),
            Atom("atom"),
            Unknown("unknown")
        }

        enum class LedgerFlowLedgerTransport(val key: String) {
            Bluetooth("bluetooth"),
            Usb("usb")
        }

        enum class LedgerDeviceViewFrom(val key: String) {
            Settings("settings"),
            WalletHeader("wallet_header"),
            SetupSection("setup_section")
        }

        enum class LedgerMigrationStartFrom(val key: String) {
            Tooltip("tooltip"),
            Settings("settings"),
            Import("import"),
            Startup("startup")
        }

        enum class LedgerDiscoveryErrorFrom(val key: String) {
            Import("import"),
            Migration("migration"),
            Repair("repair"),
            AddNetworks("add_networks")
        }

        /** ledger_request_result */
        fun ledgerRequestResult(
            purpose: LedgerFlowPurpose,
            outcome: LedgerFlowOutcome,
            attempts: Int,
            durationMs: Int,
            failureType: LedgerFlowFailureType?,
            ledgerApp: LedgerFlowLedgerApp?,
            ledgerSetting: LedgerFlowLedgerSetting?,
            errorCode: Int?,
            ledgerModel: LedgerFlowLedgerModel?,
            ledgerTransport: LedgerFlowLedgerTransport?
        )

        /** ledger_device_view */
        fun ledgerDeviceView(from: LedgerDeviceViewFrom)

        /** ledger_add_networks_start */
        fun ledgerAddNetworksStart()

        /** ledger_add_networks_success */
        fun ledgerAddNetworksSuccess(addedApps: String, ledgerModel: LedgerFlowLedgerModel?)

        /** ledger_add_networks_error */
        fun ledgerAddNetworksError(
            failureType: LedgerFlowFailureType?,
            ledgerModel: LedgerFlowLedgerModel?
        )

        /** ledger_migration_tooltip_view */
        fun ledgerMigrationTooltipView()

        /** ledger_migration_start */
        fun ledgerMigrationStart(from: LedgerMigrationStartFrom)

        /** ledger_migration_success */
        fun ledgerMigrationSuccess(
            from: LedgerMigrationStartFrom,
            ledgerModel: LedgerFlowLedgerModel?
        )

        /** ledger_migration_error */
        fun ledgerMigrationError(
            from: LedgerMigrationStartFrom,
            failureType: LedgerFlowFailureType,
            ledgerModel: LedgerFlowLedgerModel?
        )

        /** ledger_discovery_error */
        fun ledgerDiscoveryError(
            from: LedgerDiscoveryErrorFrom,
            ledgerApp: LedgerFlowLedgerApp,
            failureType: LedgerFlowFailureType,
            ledgerModel: LedgerFlowLedgerModel?
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
            Bsc("bsc"),
            Polygon("polygon"),
            Avax("avax"),
            Robinhood("robinhood"),
            Sol("sol"),
            Tron("tron"),
            Btc("btc"),
            Ltc("ltc"),
            Doge("doge"),
            Bch("bch"),
            Lighter("lighter"),
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

        /** onboarding_view_notifications */
        fun onboardingViewNotifications()

        /** onboarding_click_customize_continue */
        fun onboardingClickCustomizeContinue()
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


    interface Perps {

        enum class PerpsEntryPoint(val key: String) {
            MainBanner("main_banner"),
            MainCarousel("main_carousel"),
            MainPositions("main_positions"),
            AssetPage("asset_page"),
            Search("search"),
            TradeTab("trade_tab"),
            Loyalty("loyalty"),
            Other("other")
        }

        enum class PerpsWalletMode(val key: String) {
            Multi("multi"),
            Single("single")
        }

        enum class PerpsAmountBucket(val key: String) {
            _0("0"),
            _01("0-1"),
            _110("1-10"),
            _10100("10-100"),
            _1001k("100-1k"),
            _1k10k("1k-10k"),
            _10k100k("10k-100k"),
            _100k("100k+")
        }

        enum class PerpsScreen(val key: String) {
            Amount("amount"),
            Confirm("confirm"),
            Background("background")
        }

        enum class PerpsErrorType(val key: String) {
            BelowMinimum("below_minimum"),
            InsufficientBalance("insufficient_balance"),
            InsufficientNetworkFee("insufficient_network_fee"),
            NoQuote("no_quote"),
            ServiceUnavailable("service_unavailable"),
            QuoteExpired("quote_expired"),
            QuoteChanged("quote_changed"),
            DepositInProgress("deposit_in_progress"),
            AddressBlocked("address_blocked"),
            Failed("failed")
        }

        enum class PerpsSourceScreen(val key: String) {
            PerpsHome("perps_home"),
            OpenPosition("open_position"),
            AddToPosition("add_to_position"),
            AddMargin("add_margin")
        }

        enum class PerpsSide(val key: String) {
            Long("long"),
            Short("short")
        }

        enum class PerpsOrderType(val key: String) {
            Market("market"),
            Limit("limit")
        }

        /** perps_section_opened */
        fun perpsSectionOpened(entryPoint: PerpsEntryPoint)

        /** perps_deposit_started */
        fun perpsDepositStarted(walletMode: PerpsWalletMode, eligibleAssetsCount: Int?)

        /** perps_deposit_continue_clicked */
        fun perpsDepositContinueClicked(asset: String, amountBucket: PerpsAmountBucket?)

        /** perps_deposit_failed */
        fun perpsDepositFailed(screen: PerpsScreen, errorType: PerpsErrorType)

        /** perps_deposit_confirmed */
        fun perpsDepositConfirmed(
            sourceScreen: PerpsSourceScreen,
            asset: String,
            operationId: String
        )

        /** perps_order_submitted */
        fun perpsOrderSubmitted(
            side: PerpsSide,
            asset: String,
            leverage: Double,
            orderType: PerpsOrderType,
            clientOrderIndex: Long
        )

        /** perps_trading_data_error */
        fun perpsTradingDataError(asset: String)
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
            startedAtMs: Long,
            otherMetadata: String?
        )

        /** op_terminal */
        fun opTerminal(
            operationId: String,
            flow: RedOperationsFlow,
            operation: RedOperationsOperation,
            outcome: RedOperationsOutcome,
            durationMs: Double,
            finishedAtMs: Long,
            errorCode: Int?,
            errorMessage: String?,
            errorType: String?,
            stage: String?,
            otherMetadata: String?
        )
    }


    interface Scanner {

        enum class ScannerInput(val key: String) {
            Camera("camera"),
            Gallery("gallery")
        }

        enum class ScannerErrorType(val key: String) {
            NoCode("no_code"),
            InvalidLink("invalid_link"),
            UnsupportedHere("unsupported_here")
        }

        /** scan_open */
        fun scanOpen()

        /** scan_success */
        fun scanSuccess(input: ScannerInput)

        /** scan_error */
        fun scanError(input: ScannerInput, errorType: ScannerErrorType)
    }


    interface ScreenRender {

        enum class ScreenRenderScreen(val key: String) {
            Portfolio("portfolio"),
            History("history"),
            Trade("trade"),
            Browser("browser")
        }

        enum class ScreenRenderWalletMode(val key: String) {
            Multi("multi"),
            Single("single")
        }

        enum class ScreenRenderTrigger(val key: String) {
            InApp("in_app"),
            AppLaunch("app_launch"),
            Deeplink("deeplink"),
            Push("push"),
            Auto("auto")
        }

        enum class ScreenRenderOutcome(val key: String) {
            Fresh("fresh"),
            CachedOnly("cached_only"),
            Failed("failed"),
            Abandoned("abandoned"),
            Timeout("timeout")
        }

        enum class ScreenRenderErrorKind(val key: String) {
            Timeout("timeout"),
            Offline("offline"),
            Network("network"),
            Server("server"),
            Decode("decode"),
            Unknown("unknown")
        }

        enum class ScreenRenderNetworkType(val key: String) {
            Wifi("wifi"),
            Cellular("cellular"),
            Wired("wired"),
            Other("other"),
            None("none")
        }

        enum class ScreenRenderThermalState(val key: String) {
            Nominal("nominal"),
            Fair("fair"),
            Serious("serious"),
            Critical("critical")
        }

        /** screen_content_rendered */
        fun screenContentRendered(
            screen: ScreenRenderScreen,
            walletMode: ScreenRenderWalletMode,
            trigger: ScreenRenderTrigger,
            outcome: ScreenRenderOutcome,
            errorKind: ScreenRenderErrorKind?,
            isFirstOpen: Boolean,
            cachedMs: Long?,
            freshMs: Long?,
            screenCreatedMs: Long?,
            screenWillShowMs: Long?,
            screenShownMs: Long?,
            requestMs: Long?,
            marks: String?,
            pausedMs: Long?,
            hadPasscode: Boolean?,
            networkType: ScreenRenderNetworkType,
            isLowPower: Boolean,
            thermalState: ScreenRenderThermalState
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
            WalletTg("walletTg"),
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

        enum class TransactionSentLedgerModel(val key: String) {
            NanoS("nano_s"),
            NanoSPlus("nano_s_plus"),
            NanoX("nano_x"),
            Stax("stax"),
            Flex("flex"),
            NanoGen5("nano_gen5"),
            Atom("atom"),
            Unknown("unknown")
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
            isLiquid: Boolean?,
            ledgerModel: TransactionSentLedgerModel?
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
            Settings("settings"),
            DeepLink("deep_link")
        }

        enum class WalletFlowWalletSource(val key: String) {
            Mnemonic("mnemonic"),
            Ledger("ledger"),
            Signer("signer"),
            Keystone("keystone"),
            Privatekey("privatekey"),
            Watchonly("watchonly")
        }

        enum class WalletFlowPairingEntry(val key: String) {
            Menu("menu"),
            Deeplink("deeplink"),
            AppQrScanner("app_qr_scanner")
        }

        enum class WalletFlowPairingAction(val key: String) {
            CameraOpened("camera_opened"),
            SignerAppOpened("signer_app_opened"),
            DeviceSearchStarted("device_search_started")
        }

        enum class WalletFlowLedgerTransport(val key: String) {
            Bluetooth("bluetooth"),
            Usb("usb")
        }

        enum class WalletFlowLedgerModel(val key: String) {
            NanoS("nano_s"),
            NanoSPlus("nano_s_plus"),
            NanoX("nano_x"),
            Stax("stax"),
            Flex("flex"),
            NanoGen5("nano_gen5"),
            Atom("atom"),
            Unknown("unknown")
        }

        enum class WalletFlowPairingLastStep(val key: String) {
            None("none"),
            Action("action"),
            DeviceFound("device_found"),
            Connected("connected"),
            AppOpened("app_opened"),
            AccountsView("accounts_view")
        }

        enum class WalletFlowPairingExitReason(val key: String) {
            Back("back"),
            BluetoothOff("bluetooth_off"),
            BluetoothPermissionDenied("bluetooth_permission_denied"),
            CameraPermissionDenied("camera_permission_denied"),
            Error("error"),
            Other("other")
        }

        enum class WalletFlowBalanceBucket(val key: String) {
            _0("0"),
            _01("0-1"),
            _110("1-10"),
            _10100("10-100"),
            _1001k("100-1k"),
            _1k10k("1k-10k"),
            _10k100k("10k-100k"),
            _100k("100k+")
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
            from: WalletFlowFrom,
            pairingEntry: WalletFlowPairingEntry?
        )

        /** wallet_import_pairing_action */
        fun walletImportPairingAction(
            walletMode: WalletFlowWalletMode,
            walletSource: WalletFlowWalletSource,
            from: WalletFlowFrom,
            pairingAction: WalletFlowPairingAction
        )

        /** wallet_import_ledger_device_found */
        fun walletImportLedgerDeviceFound(
            walletMode: WalletFlowWalletMode,
            walletSource: WalletFlowWalletSource,
            from: WalletFlowFrom,
            ledgerTransport: WalletFlowLedgerTransport,
            ledgerModel: WalletFlowLedgerModel?
        )

        /** wallet_import_pairing_exit */
        fun walletImportPairingExit(
            walletMode: WalletFlowWalletMode,
            walletSource: WalletFlowWalletSource,
            from: WalletFlowFrom,
            pairingLastStep: WalletFlowPairingLastStep,
            pairingExitReason: WalletFlowPairingExitReason
        )

        /** wallet_import_mnemonic_accepted */
        fun walletImportMnemonicAccepted(
            walletMode: WalletFlowWalletMode,
            walletSource: WalletFlowWalletSource,
            from: WalletFlowFrom
        )

        /** wallet_import_signer_scanned */
        fun walletImportSignerScanned(
            walletMode: WalletFlowWalletMode,
            walletSource: WalletFlowWalletSource,
            from: WalletFlowFrom
        )

        /** wallet_import_keystone_scanned */
        fun walletImportKeystoneScanned(
            walletMode: WalletFlowWalletMode,
            walletSource: WalletFlowWalletSource,
            from: WalletFlowFrom
        )

        /** wallet_import_ledger_bluetooth_connected */
        fun walletImportLedgerBluetoothConnected(
            walletMode: WalletFlowWalletMode,
            walletSource: WalletFlowWalletSource,
            from: WalletFlowFrom,
            ledgerModel: WalletFlowLedgerModel?
        )

        /** wallet_import_ledger_usb_connected */
        fun walletImportLedgerUsbConnected(
            walletMode: WalletFlowWalletMode,
            walletSource: WalletFlowWalletSource,
            from: WalletFlowFrom,
            ledgerModel: WalletFlowLedgerModel?
        )

        /** wallet_import_ledger_app_opened */
        fun walletImportLedgerAppOpened(
            walletMode: WalletFlowWalletMode,
            walletSource: WalletFlowWalletSource,
            from: WalletFlowFrom,
            ledgerModel: WalletFlowLedgerModel?
        )

        /** wallet_import_ledger_accounts_view */
        fun walletImportLedgerAccountsView(
            walletMode: WalletFlowWalletMode,
            walletSource: WalletFlowWalletSource,
            from: WalletFlowFrom,
            ledgerModel: WalletFlowLedgerModel?,
            ledgerApps: String
        )

        /** wallet_import_error */
        fun walletImportError(
            walletMode: WalletFlowWalletMode,
            walletSource: WalletFlowWalletSource,
            from: WalletFlowFrom,
            errorType: String?,
            errorCode: Int?,
            errorMessage: String?,
            ledgerModel: WalletFlowLedgerModel?
        )

        /** wallet_import_success */
        fun walletImportSuccess(
            walletMode: WalletFlowWalletMode,
            walletSource: WalletFlowWalletSource,
            from: WalletFlowFrom,
            pairingEntry: WalletFlowPairingEntry?,
            ledgerModel: WalletFlowLedgerModel?
        )

        /** wallet_import_balance */
        fun walletImportBalance(
            walletMode: WalletFlowWalletMode,
            walletSource: WalletFlowWalletSource,
            from: WalletFlowFrom,
            balanceBucket: WalletFlowBalanceBucket
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
            WalletTg("walletTg"),
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
            QrCode("qr_code"),
            HistoryScreen("history_screen"),
            Browser("browser"),
            Collectibles("collectibles"),
            Perps("perps"),
            Walletconnect("walletconnect"),
            Tonconnect("tonconnect"),
            Staking("staking"),
            Migration("migration"),
            Other("other")
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
