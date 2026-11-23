package com.tonapps.tonkeeper.ui.screen.battery.refill

import androidx.annotation.StringRes
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.tonkeeper.ui.screen.battery.refill.entity.PromoState

data class BatteryRefillUiState(
    val header: HeaderUi,
    val promo: PromoUi?,
    val methodLoading: Boolean,
    val packs: List<PackUi>,
    val button: ButtonUi?,
    val methods: List<PayMethodRowUi>,
    val giftVisible: Boolean,
    val chargingEnabled: Boolean,
    val busy: Boolean,
) {
    val method: PayMethodRowUi? = methods.firstOrNull { it.selected }
}

data class HeaderUi(
    val level: Float,
    val charges: Int,
    val formattedCharges: String,
    val isNegative: Boolean,
    @StringRes val captionRes: Int,
)

data class PromoUi(
    val state: PromoState,
    val code: String?,
)

data class PackUi(
    val id: String,
    val title: String,
    val caption: String,
    val level: Float,
    val enabled: Boolean,
    val selected: Boolean,
    val bestValue: Boolean,
    val isOther: Boolean,
)

data class ButtonUi(
    val text: String,
    val enabled: Boolean,
)

enum class PayMethodKind {
    Token,
    Asset,
    Store,
}

data class PayMethodRowUi(
    val id: String,
    val title: String,
    val caption: String,
    val imageUrl: String?,
    val chainBadge: Chain?,
    val kind: PayMethodKind,
    val selected: Boolean,
)
