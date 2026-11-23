package com.tonapps.tonkeeper.ui.screen.battery.refill

const val STORE_PAY_METHOD = "store"

data class PackChoice(val id: String, val charges: Int, val enabled: Boolean)

data class ButtonChoice(val title: String?, val enabled: Boolean)

sealed interface MethodChoice {
    data object Loading : MethodChoice
    data object Store : MethodChoice
    data class Token(val address: String) : MethodChoice
    data object None : MethodChoice
}

fun selectPack(previous: String?, packs: List<PackChoice>): String? {
    if (packs.any { it.id == previous && it.enabled }) {
        return previous
    }
    val enabled = packs.filter { it.enabled }
    if (enabled.isNotEmpty()) {
        return enabled[enabled.size / 2].id
    }
    return previous?.takeIf { id -> packs.any { it.id == id } }
}

fun bestValueId(packs: List<PackChoice>): String? {
    if (packs.size > 1) {
        return packs.maxBy { it.charges }.id
    }
    return null
}

fun resolveMethod(stored: String?, tokens: List<String>?, storeAvailable: Boolean?): MethodChoice {
    val storedToken = stored?.takeIf { it != STORE_PAY_METHOD }
    return when {
        storedToken != null && tokens == null -> MethodChoice.Loading
        storedToken != null && tokens.orEmpty().contains(storedToken) -> MethodChoice.Token(storedToken)
        storeAvailable == true -> MethodChoice.Store
        storeAvailable == null || tokens == null -> MethodChoice.Loading
        else -> tokens.firstOrNull()?.let { MethodChoice.Token(it) } ?: MethodChoice.None
    }
}

fun storeAvailability(
    staticAllowed: Boolean,
    iapKnown: Boolean,
    productsLoaded: Boolean,
    hasPricedPack: Boolean,
): Boolean? {
    return when {
        !staticAllowed -> false
        !productsLoaded || !iapKnown -> null
        else -> hasPricedPack
    }
}

fun buttonState(
    hasPacks: Boolean,
    selectedTitle: String?,
    selectedEnabled: Boolean,
    busy: Boolean,
): ButtonChoice? {
    return when {
        !hasPacks -> null
        selectedTitle != null -> ButtonChoice(selectedTitle, selectedEnabled && !busy)
        else -> ButtonChoice(null, false)
    }
}
