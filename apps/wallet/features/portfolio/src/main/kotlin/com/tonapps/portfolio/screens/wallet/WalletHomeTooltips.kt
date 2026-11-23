package com.tonapps.portfolio.screens.wallet

import com.tonapps.core.flags.EarnTooltipInteractor
import com.tonapps.core.flags.TooltipManager
import com.tonapps.core.flags.WalletTooltipKey
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold

enum class PerpsShelfVisibility { Gone, Partial, Full }

internal enum class HomeTooltip { Earn, Perps }

internal class WalletHomeTooltips(
    private val earnTooltipInteractor: EarnTooltipInteractor,
    private val settingsRepository: SettingsRepository,
) {

    private val earnAllowed = MutableStateFlow(false)
    private val perpsAllowed = MutableStateFlow(false)
    private val perpsShelf = MutableStateFlow(PerpsShelfVisibility.Gone)
    private var perpsOnScreen = false

    fun active(onScreen: Flow<Boolean>, earn: Flow<WalletSection.Earn?>): Flow<HomeTooltip?> = combine(
        onScreen,
        earn,
        earnAllowed,
        perpsAllowed,
        perpsShelf,
    ) { onScreen, earn, earnAllowed, perpsAllowed, shelf ->
        Inputs(
            earn = onScreen && earnAllowed && earn is WalletSection.Earn.Vaults,
            perpsStart = onScreen && perpsAllowed && shelf == PerpsShelfVisibility.Full,
            perpsStay = onScreen && perpsAllowed && shelf != PerpsShelfVisibility.Gone,
        )
    }
        .runningFold(null as HomeTooltip?) { current, inputs ->
            when {
                current == HomeTooltip.Perps && inputs.perpsStay -> HomeTooltip.Perps
                inputs.earn -> HomeTooltip.Earn
                inputs.perpsStart -> HomeTooltip.Perps
                else -> null
            }
        }
        .distinctUntilChanged()
        .onEach { tooltip ->
            if (tooltip == HomeTooltip.Earn) {
                earnTooltipInteractor.markShown()
            }
            if (tooltip == HomeTooltip.Perps) {
                TooltipManager.incrementShowCount(WalletTooltipKey.PERPS_SECTION)
            } else if (perpsOnScreen) {
                perpsAllowed.value = false
            }
            perpsOnScreen = tooltip == HomeTooltip.Perps
        }

    suspend fun load() {
        earnAllowed.value = earnTooltipInteractor.canShow()
        perpsAllowed.value = canShowPerps()
    }

    fun recheckPerps() {
        if (perpsAllowed.value && !canShowPerps()) {
            perpsAllowed.value = false
        }
    }

    fun dismissEarn() {
        earnAllowed.value = false
    }

    fun dismissPerps() {
        perpsAllowed.value = false
    }

    fun setPerpsShelf(visibility: PerpsShelfVisibility) {
        perpsShelf.value = visibility
    }

    private fun canShowPerps(): Boolean {
        return !settingsRepository.perpsTermsAccepted && TooltipManager.shouldShow(WalletTooltipKey.PERPS_SECTION)
    }

    private data class Inputs(val earn: Boolean, val perpsStart: Boolean, val perpsStay: Boolean)
}
