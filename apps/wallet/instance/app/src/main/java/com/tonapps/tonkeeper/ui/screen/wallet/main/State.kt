package com.tonapps.tonkeeper.ui.screen.wallet.main

import android.content.Context
import android.net.Uri
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.legacy.enteties.AssetsEntity
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.core.BalanceType
import com.tonapps.legacy.assets.sumOfVerifiedFiat
import com.tonapps.tonkeeper.manager.apk.APKManager
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import uikit.widget.BatteryView
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.entity.BannerEntity
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.NotificationEntity
import com.tonapps.wallet.data.collectibles.entities.DnsExpiringEntity
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.core.isAvailableBiometric
import com.tonapps.wallet.data.dapps.entities.AppPushEntity
import com.tonapps.wallet.data.rates.entity.RatesEntity
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import io.tonapi.models.WalletPlugin

sealed class State {

    private enum class SetupType {
        Push,
        Biometry,
        Telegram,
        Backup,
        SafeMode,
        OnboardingStories,
    }

    data class Battery(
        val balance: Coins,
        val beta: Boolean,
        val disabled: Boolean,
        val viewed: Boolean,
    ): State()

    data class Setup(
        val pushEnabled: Boolean,
        val biometryEnabled: Boolean,
        val hasBackup: Boolean,
        val showTelegramChannel: Boolean,
        val safeModeBlock: Boolean,
        val onboardingStoriesEnabled: Boolean,
    ): State()

    data class Assets(
        val currency: WalletCurrency,
        val list: List<AssetsEntity>,
        val fromCache: Boolean,
        val rates: RatesEntity,
    ): State() {

        val size: Int
            get() = list.size

        fun getTotalBalanceFiat(wallet: WalletEntity): Coins {
            return if (wallet.testnet) {
                list.first().fiat
            } else {
                list.sumOfVerifiedFiat()
            }
        }

        fun getBalanceType(wallet: WalletEntity): Int {
            val balanceFiat = getTotalBalanceFiat(wallet)
            val balanceTON = rates.convertFromFiat(TokenEntity.TON.address, balanceFiat)
            return BalanceType.getBalanceType(balanceTON)
        }

        fun getTotalBalanceFormat(
            wallet: WalletEntity,
        ): CharSequence {
            val total = getTotalBalanceFiat(wallet)
            return CurrencyFormatter.formatFiat(currency.code, total)
        }
    }

    data class Main(
        val wallet: WalletEntity,
        val assets: Assets,
        val hasBackup: Boolean,
        val battery: Battery,
        val lt: Long?,
        val isOnline: Boolean,
        val apkStatus: APKManager.Status,
        val plugins: List<WalletPlugin>,
        val maxStakingApyFormatted: String? = null,
        val banners: List<BannerEntity> = emptyList(),
        val collectibles: List<NftEntity> = emptyList(),
        val allCollectiblesHidden: Boolean = false,
    ): State() {

        val totalBalanceFiat: Coins
            get() = assets.getTotalBalanceFiat(wallet)

        private val totalBalanceFormat: CharSequence
            get() = assets.getTotalBalanceFormat(wallet)

        private val balanceType: Int
            get() = assets.getBalanceType(wallet)

        private fun uiItemsTokens(
            hiddenBalance: Boolean,
            assetsExpanded: Boolean,
        ): List<Item> {
            val currencyCode = assets.currency.code
            val uiItems = mutableListOf<Item>()
            uiItems.add(Item.AssetsHeader(wallet))

            val loadedAssetsCount = assets.list.size
            val hasMoreAssets = loadedAssetsCount > PREVIEW_ASSETS_LIMIT
            val showMoreButton = hasMoreAssets && !assetsExpanded
            val visibleAssetsCount = when {
                assetsExpanded -> loadedAssetsCount
                hasMoreAssets -> PREVIEW_ASSETS_LIMIT - 1
                else -> loadedAssetsCount
            }
            val bundleSize = if (showMoreButton) {
                visibleAssetsCount + 1
            } else {
                visibleAssetsCount
            }

            for (index in 0 until visibleAssetsCount) {
                val asset = assets.list[index]
                val position = ListCell.getPosition(bundleSize, index)
                if (asset is AssetsEntity.Staked) {
                    val staked = asset.staked
                    val item = Item.Stake(
                        position = position,
                        poolAddress = staked.pool.address,
                        poolName = staked.pool.name,
                        poolImplementation = staked.pool.implementation,
                        balance = staked.balance,
                        balanceFormat = CurrencyFormatter.format(value = staked.balance, compact = true),
                        message = null,
                        fiat = staked.fiatBalance,
                        fiatFormat = CurrencyFormatter.formatFiat(currencyCode, staked.fiatBalance, compact = true),
                        hiddenBalance = hiddenBalance,
                        wallet = wallet,
                        readyWithdraw = staked.readyWithdraw,
                        readyWithdrawFormat = CurrencyFormatter.formatFiat("TON", staked.readyWithdraw, compact = true),
                        pendingDeposit = staked.pendingDeposit,
                        pendingDepositFormat = CurrencyFormatter.formatFiat("TON", staked.pendingDeposit, compact = true),
                        pendingWithdraw = staked.pendingWithdraw,
                        pendingWithdrawFormat = CurrencyFormatter.formatFiat("TON", staked.pendingWithdraw, compact = true),
                        cycleEnd = staked.cycleEnd,
                        apy = staked.pool.apy
                    )
                    uiItems.add(item)
                } else if (asset is AssetsEntity.Token) {
                    val item = Item.Token(
                        position = position,
                        token = asset.token,
                        hiddenBalance = hiddenBalance,
                        testnet = wallet.testnet,
                        currencyCode = currencyCode,
                        wallet = wallet,
                        showNetwork = asset.token.isUsdt || asset.token.isTrc20,
                        apyFormatted = maxStakingApyFormatted.takeIf { asset.token.isTon },
                    )
                    uiItems.add(item)
                }
            }

            if (showMoreButton) {
                val previewIcons = buildList {
                    val end = minOf(loadedAssetsCount, visibleAssetsCount + MORE_ASSETS_PREVIEW_ICONS)
                    for (i in visibleAssetsCount until end) {
                        assetIconUri(assets.list[i])?.let { add(it) }
                    }
                }
                uiItems.add(Item.MoreAssets(
                    position = ListCell.getPosition(bundleSize, bundleSize - 1),
                    wallet = wallet,
                    iconUris = previewIcons,
                ))
            }

            uiItems.add(Item.Space(true))
            return uiItems.toList()
        }

        private fun assetIconUri(asset: AssetsEntity): Uri? = when (asset) {
            is AssetsEntity.Token -> asset.token.imageUri
            else -> null
        }

        private fun uiItemBalance(
            hiddenBalance: Boolean,
            status: Item.Status,
            lastUpdatedFormat: String,
            prefixYourAddress: Boolean,
        ): Item.Balance {
            return Item.Balance(
                balance = totalBalanceFormat,
                wallet = wallet,
                status = status,
                hiddenBalance = hiddenBalance,
                hasBackup = hasBackup,
                balanceType = balanceType,
                lastUpdatedFormat = lastUpdatedFormat,
                batteryBalance = battery.balance,
                showBattery = !battery.disabled,
                batteryEmptyState = when {
                    battery.balance.isNegative -> BatteryView.EmptyState.ACCENT_RED
                    battery.viewed -> BatteryView.EmptyState.SECONDARY
                    else -> BatteryView.EmptyState.ACCENT
                },
                prefixYourAddress = prefixYourAddress
            )
        }

        private fun uiItemActions(
            config: ConfigEntity,
        ): Item.Actions {
            return Item.Actions(
                wallet = wallet,
                token = TokenEntity.TON,
                swapUri = config.swapUri,
                isSwapDisabled = config.flags.disableSwap,
                isStakingDisabled = config.flags.disableStaking,
                isExchangeDisabled = config.flags.disableExchangeMethods
            )
        }

        private fun uiItemsSetup(
            walletId: String,
            config: ConfigEntity,
            setupTypes: List<SetupType>
        ): List<Item> {
            val uiItems = mutableListOf<Item>()
            uiItems.add(Item.SetupTitle(
                walletId = walletId,
                showDone = !setupTypes.contains(SetupType.Backup)
            ))
            for ((index, setupType) in setupTypes.withIndex()) {
                val position = ListCell.getPosition(setupTypes.size, index)
                val item = when (setupType) {
                    SetupType.Backup -> Item.SetupLink(
                        position = position,
                        iconRes = UIKitIcon.ic_key_28,
                        textRes = Localization.setup_finish_backup,
                        subtitleRes = Localization.setup_finish_backup_subtitle,
                        link = "tonkeeper://backups",
                        blue = false,
                        walletId = wallet.id,
                        settingsType = Item.SetupLink.TYPE_NONE
                    )
                    SetupType.Telegram -> Item.SetupLink(
                        position = position,
                        iconRes = UIKitIcon.ic_telegram_28,
                        textRes = Localization.setup_finish_telegram,
                        link = config.tonkeeperNewsUrl,
                        blue = true,
                        walletId = wallet.id,
                        settingsType = Item.SetupLink.TYPE_TELEGRAM_CHANNEL
                    )
                    SetupType.OnboardingStories -> Item.SetupLink(
                        position = position,
                        iconRes = UIKitIcon.ic_stories_44,
                        textRes = Localization.setup_onboarding,
                        link = "tonkeeper://stories/onboarding",
                        blue = false,
                        walletId = wallet.id,
                        settingsType = Item.SetupLink.TYPE_STORIES
                    )
                    SetupType.Biometry -> Item.SetupSwitch(
                        position = position,
                        iconRes = UIKitIcon.ic_faceid_28,
                        textRes = Localization.setup_finish_biometry,
                        enabled = false,
                        wallet = wallet,
                        settingsType = Item.SetupSwitch.TYPE_BIOMETRIC
                    )
                    SetupType.Push -> Item.SetupSwitch(
                        position = position,
                        iconRes = UIKitIcon.ic_bell_28,
                        textRes = Localization.setup_finish_push,
                        enabled = false,
                        wallet = wallet,
                        settingsType = Item.SetupSwitch.TYPE_PUSH
                    )
                    SetupType.SafeMode -> Item.SetupLink(
                        position = position,
                        iconRes = UIKitIcon.ic_control_28,
                        textRes = Localization.setup_safe_mode,
                        link = "tonkeeper://security",
                        blue = true,
                        walletId = wallet.id,
                        settingsType = Item.SetupLink.TYPE_SAFE_MODE
                    )
                }
                uiItems.add(item)
            }
            return uiItems.toList()
        }

        private fun createSetupTypes(
            setup: Setup,
        ): List<SetupType> {
            val setupTypes = mutableListOf<SetupType>()
            if (!hasBackup) {
                setupTypes.add(SetupType.Backup)
            }
            if (!setup.pushEnabled) {
                setupTypes.add(SetupType.Push)
            }
            if (!setup.biometryEnabled && isAvailableBiometric(App.instance)) {
                setupTypes.add(SetupType.Biometry)
            }
            if (setup.showTelegramChannel) {
                setupTypes.add(SetupType.Telegram)
            }
            if (setup.safeModeBlock) {
                setupTypes.add(SetupType.SafeMode)
            }
            if (setup.onboardingStoriesEnabled) {
                setupTypes.add(SetupType.OnboardingStories)
            }

            return setupTypes.toList()
        }

        fun uiItems(
            context: Context,
            wallet: WalletEntity,
            hiddenBalance: Boolean,
            status: Item.Status,
            config: ConfigEntity,
            alerts: List<NotificationEntity>,
            dAppNotifications: DAppNotifications,
            setup: Setup?,
            lastUpdatedFormat: String,
            prefixYourAddress: Boolean,
            renewDomains: List<DnsExpiringEntity>,
            assetsExpanded: Boolean,
        ): List<Item> {
            val uiItems = mutableListOf<Item>()
            if (apkStatus != APKManager.Status.Default && apkStatus !is APKManager.Status.UpdateAvailable) {
                uiItems.add(Item.ApkStatus(apkStatus))
            }
            val legacySubscriptions = plugins.filter { it.type == "subscription_v1" }
            if (legacySubscriptions.isNotEmpty() && (wallet.hasPrivateKey || wallet.signer)) {
                uiItems.add(Item.Alert(
                    title = context.resources.getQuantityString(Plurals.legacy_subscriptions_alert_title, legacySubscriptions.size, legacySubscriptions.size),
                    message = context.resources.getQuantityString(Plurals.legacy_subscriptions_alert_message, legacySubscriptions.size, legacySubscriptions.size),
                    buttonTitle = context.getString(Localization.manage),
                    buttonUrl = "tonkeeper://extensions"
                ))
                uiItems.add(Item.Space(true))
            }
            if (alerts.isNotEmpty()) {
                for (alert in alerts) {
                    uiItems.add(Item.Alert(alert))
                    uiItems.add(Item.Space(true))
                }
            }
            uiItems.add(uiItemBalance(hiddenBalance, status, lastUpdatedFormat, prefixYourAddress))
            uiItems.add(uiItemActions(config))
            if (banners.isNotEmpty()) {
                uiItems.add(Item.Banners(walletId = wallet.id, banners = banners))
            }
            if (!dAppNotifications.isEmpty) {
                uiItems.add(Item.Push(dAppNotifications.pushes))
            }

            if (renewDomains.isNotEmpty()) {
                uiItems.add(Item.RenewDomains(wallet, renewDomains))
            }

            setup?.let {
                val setupTypes = createSetupTypes(it)
                if (setupTypes.isNotEmpty()) {
                    uiItems.addAll(uiItemsSetup(wallet.id, config, setupTypes))
                }
            }

            uiItems.addAll(uiItemsTokens(hiddenBalance, assetsExpanded))
            if (collectibles.isNotEmpty() || allCollectiblesHidden) {
                uiItems.add(
                    Item.Collectibles(
                        wallet = wallet,
                        nfts = collectibles,
                        allHidden = allCollectiblesHidden,
                    )
                )
            }
            return uiItems.toList()
        }

        private companion object {
            const val PREVIEW_ASSETS_LIMIT = 7
            const val MORE_ASSETS_PREVIEW_ICONS = 2
        }
    }

    data class DAppNotifications(
        val pushes: List<AppPushEntity> = emptyList(),
    ): State() {

        val isEmpty: Boolean
            get() = pushes.isEmpty()
    }

    data class Settings(
        val hiddenBalance: Boolean,
        val config: ConfigEntity,
        val status: Item.Status
    ): State()
}