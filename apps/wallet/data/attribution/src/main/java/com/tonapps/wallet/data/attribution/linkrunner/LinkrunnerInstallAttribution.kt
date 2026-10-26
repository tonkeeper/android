package com.tonapps.wallet.data.attribution.linkrunner

import com.tonapps.wallet.data.attribution.entities.AttributionTouch

internal sealed interface LinkrunnerInstallAttribution {

    data class Attributed(val touch: AttributionTouch) : LinkrunnerInstallAttribution

    data object NotAttributed : LinkrunnerInstallAttribution

    data object Unavailable : LinkrunnerInstallAttribution
}
