package com.tonapps.wallet.data.loyalty

import com.tonapps.wallet.api.entity.LoyaltyProfileEntity

data class LoyaltyEntry(
    val showsSection: Boolean,
    val showsBanner: Boolean,
    val ohms: String?,
) {

    val positiveOhms: String? = ohms?.takeIf { it.toBigDecimalOrNull()?.signum() == 1 }

    companion object {

        const val OHM_ASSET = "OHM"

        val Hidden = LoyaltyEntry(showsSection = false, showsBanner = false, ohms = null)

        internal fun of(profile: LoyaltyProfileEntity?, isBannerHidden: Boolean): LoyaltyEntry {
            if (profile == null || !profile.showSection) {
                return Hidden
            }
            return LoyaltyEntry(
                showsSection = true,
                showsBanner = profile.showBanner && !isBannerHidden,
                ohms = profile.balances
                    .firstOrNull { it.asset == OHM_ASSET }
                    ?.available
                    ?.takeIf { it.isNotBlank() },
            )
        }
    }
}
