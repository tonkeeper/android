package com.tonapps.bus.generated

import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.flows.AssetScreenImpl
import com.tonapps.bus.generated.flows.BatteryNativeImpl
import com.tonapps.bus.generated.flows.DappBrowserImpl
import com.tonapps.bus.generated.flows.DepositFlowImpl
import com.tonapps.bus.generated.flows.HomeBannerImpl
import com.tonapps.bus.generated.flows.InappReviewImpl
import com.tonapps.bus.generated.flows.InstallAppImpl
import com.tonapps.bus.generated.flows.LaunchAppImpl
import com.tonapps.bus.generated.flows.MigrationImpl
import com.tonapps.bus.generated.flows.MysteryRaffleImpl
import com.tonapps.bus.generated.flows.OnboardingFlowImpl
import com.tonapps.bus.generated.flows.OnrampsNativeImpl
import com.tonapps.bus.generated.flows.PasscodeLockoutImpl
import com.tonapps.bus.generated.flows.PushClickImpl
import com.tonapps.bus.generated.flows.RedOperationsImpl
import com.tonapps.bus.generated.flows.SendNativeImpl
import com.tonapps.bus.generated.flows.StakingNativeImpl
import com.tonapps.bus.generated.flows.StoriesImpl
import com.tonapps.bus.generated.flows.SwapsNativeImpl
import com.tonapps.bus.generated.flows.TonConnectImpl
import com.tonapps.bus.generated.flows.TradeUiFlowImpl
import com.tonapps.bus.generated.flows.TransactionSentImpl
import com.tonapps.bus.generated.flows.TwaSunsetImpl
import com.tonapps.bus.generated.flows.UserErrorsImpl
import com.tonapps.bus.generated.flows.WalletFlowImpl
import com.tonapps.bus.generated.flows.WalletOpenImpl
import com.tonapps.bus.generated.flows.WithdrawFlowImpl

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class DefaultEvents(
    private val eventExecutor: EventExecutor,
) {

    val assetScreen = AssetScreenImpl(eventExecutor)
    val batteryNative = BatteryNativeImpl(eventExecutor)
    val dappBrowser = DappBrowserImpl(eventExecutor)
    val depositFlow = DepositFlowImpl(eventExecutor)
    val homeBanner = HomeBannerImpl(eventExecutor)
    val inappReview = InappReviewImpl(eventExecutor)
    val installApp = InstallAppImpl(eventExecutor)
    val launchApp = LaunchAppImpl(eventExecutor)
    val migration = MigrationImpl(eventExecutor)
    val mysteryRaffle = MysteryRaffleImpl(eventExecutor)
    val onboardingFlow = OnboardingFlowImpl(eventExecutor)
    val onrampsNative = OnrampsNativeImpl(eventExecutor)
    val passcodeLockout = PasscodeLockoutImpl(eventExecutor)
    val pushClick = PushClickImpl(eventExecutor)
    val redOperations = RedOperationsImpl(eventExecutor)
    val sendNative = SendNativeImpl(eventExecutor)
    val stakingNative = StakingNativeImpl(eventExecutor)
    val stories = StoriesImpl(eventExecutor)
    val swapsNative = SwapsNativeImpl(eventExecutor)
    val tonConnect = TonConnectImpl(eventExecutor)
    val tradeUiFlow = TradeUiFlowImpl(eventExecutor)
    val transactionSent = TransactionSentImpl(eventExecutor)
    val twaSunset = TwaSunsetImpl(eventExecutor)
    val userErrors = UserErrorsImpl(eventExecutor)
    val walletFlow = WalletFlowImpl(eventExecutor)
    val walletOpen = WalletOpenImpl(eventExecutor)
    val withdrawFlow = WithdrawFlowImpl(eventExecutor)
}
