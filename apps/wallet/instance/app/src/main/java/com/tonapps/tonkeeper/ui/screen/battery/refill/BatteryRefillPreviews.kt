package com.tonapps.tonkeeper.ui.screen.battery.refill

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tonapps.tonkeeper.ui.screen.battery.refill.entity.PromoState
import com.tonapps.wallet.localization.Localization
import ui.preview.ThemedPreview

private val previewCallbacks = BatteryRefillCallbacks(
    onClose = {},
    onPackClick = {},
    onOtherClick = {},
    onPayWithClick = {},
    onTopUp = {},
    onGiftClick = {},
    onHistoryClick = {},
    onPromoSubmit = {},
    onRestoreClick = {},
)

private val previewHeader = HeaderUi(
    level = 0.6f,
    charges = 120,
    formattedCharges = "120",
    isNegative = false,
    captionRes = Localization.battery_refill_subtitle,
)

private val storePacks = listOf(
    previewPack("small", "$0.99", "250 charges", 0.25f, selected = false, bestValue = false),
    previewPack("medium", "$4.99", "1,250 charges", 0.5f, selected = true, bestValue = false),
    previewPack("large", "$9.99", "2,500 charges", 1f, selected = false, bestValue = true),
)

private val otherPack = PackUi(
    id = "other",
    title = "Other",
    caption = "Enter amount manually",
    level = 0f,
    enabled = true,
    selected = false,
    bestValue = false,
    isOther = true,
)

private val tokenPacks = listOf(
    previewPack("large", "10 USD₮", "2,500 charges", 1f, selected = false, bestValue = true),
    previewPack("medium", "5 USD₮", "1,250 charges", 0.5f, selected = true, bestValue = false),
    previewPack("small", "1 USD₮", "250 charges", 0.25f, selected = false, bestValue = false),
    otherPack,
)

private val payMethodRows = listOf(
    PayMethodRowUi(
        id = "0:usdt",
        title = "USD₮",
        caption = "Balance 3,906 USD₮",
        imageUrl = null,
        chainBadge = null,
        kind = PayMethodKind.Token,
        selected = false,
    ),
    PayMethodRowUi(
        id = "TON",
        title = "TON",
        caption = "Balance 12.5 TON",
        imageUrl = null,
        chainBadge = null,
        kind = PayMethodKind.Token,
        selected = false,
    ),
    PayMethodRowUi(
        id = "base/mainnet/erc20/0x833589fcd6edb6e08f4c7c32d4f71b54bda02913",
        title = "USDC",
        caption = "Base",
        imageUrl = null,
        chainBadge = null,
        kind = PayMethodKind.Asset,
        selected = false,
    ),
    PayMethodRowUi(
        id = STORE_PAY_METHOD,
        title = "Google Play",
        caption = "In-app purchase",
        imageUrl = null,
        chainBadge = null,
        kind = PayMethodKind.Store,
        selected = false,
    ),
)

private fun payMethodsSelecting(id: String?) = payMethodRows.map { it.copy(selected = it.id == id) }

private val storeState = BatteryRefillUiState(
    header = previewHeader,
    promo = PromoUi(PromoState.Default, null),
    methodLoading = false,
    packs = storePacks,
    button = ButtonUi("Top up for $4.99", enabled = true),
    methods = payMethodsSelecting(STORE_PAY_METHOD),
    giftVisible = true,
    chargingEnabled = true,
    busy = false,
)

private val tokenState = storeState.copy(
    methods = payMethodsSelecting("0:usdt"),
    packs = tokenPacks,
    button = ButtonUi("Top up for 5 USD₮", enabled = true),
)

private fun previewPack(
    id: String,
    title: String,
    caption: String,
    level: Float,
    selected: Boolean,
    bestValue: Boolean,
    enabled: Boolean = true,
) = PackUi(
    id = id,
    title = title,
    caption = caption,
    level = level,
    enabled = enabled,
    selected = selected,
    bestValue = bestValue,
    isOther = false,
)

@Composable
private fun PreviewScreen(state: BatteryRefillUiState) {
    Box(modifier = Modifier.height(900.dp)) {
        BatteryRefillContent(state = state, callbacks = previewCallbacks)
    }
}

@Preview(widthDp = 360)
@Composable
private fun StorePreview() {
    ThemedPreview {
        PreviewScreen(storeState)
    }
}

@Preview(widthDp = 360)
@Composable
private fun TokenPreview() {
    ThemedPreview {
        PreviewScreen(tokenState)
    }
}

@Preview(widthDp = 360)
@Composable
private fun TokenLowBalancePreview() {
    ThemedPreview {
        PreviewScreen(
            tokenState.copy(
                methods = payMethodsSelecting("0:usdt").map {
                    if (it.selected) {
                        it.copy(caption = "Balance 0.40 USD₮")
                    } else {
                        it
                    }
                },
                packs = listOf(
                    previewPack("large", "10 USD₮", "2,500 charges", 1f, selected = false, bestValue = true, enabled = false),
                    previewPack("medium", "5 USD₮", "1,250 charges", 0.5f, selected = false, bestValue = false, enabled = false),
                    previewPack("small", "1 USD₮", "250 charges", 0.25f, selected = false, bestValue = false, enabled = false),
                    otherPack,
                ),
                button = ButtonUi("Top up Battery", enabled = false),
            )
        )
    }
}

@Preview(widthDp = 360)
@Composable
private fun NegativePreview() {
    ThemedPreview {
        PreviewScreen(
            storeState.copy(
                header = previewHeader.copy(level = 0f, charges = -126, formattedCharges = "-126", isNegative = true),
            )
        )
    }
}

@Preview(widthDp = 360)
@Composable
private fun LoadingPreview() {
    ThemedPreview {
        PreviewScreen(
            storeState.copy(
                methods = payMethodsSelecting(null),
                methodLoading = true,
                packs = emptyList(),
                button = null,
            )
        )
    }
}

@Preview(widthDp = 360)
@Composable
private fun NoMethodPreview() {
    ThemedPreview {
        PreviewScreen(
            storeState.copy(
                header = previewHeader.copy(level = 0f, charges = 0, formattedCharges = "0"),
                methodLoading = false,
                packs = emptyList(),
                button = null,
                methods = emptyList(),
                giftVisible = false,
            )
        )
    }
}

@Composable
private fun PreviewPayWithSheet(methods: List<PayMethodRowUi>) {
    Column(modifier = Modifier.height(640.dp)) {
        BatteryPayWithList(methods = methods, enabled = true, onClose = {}, onSelect = {})
    }
}

@Preview(widthDp = 360)
@Composable
private fun PayWithSheetPreview() {
    ThemedPreview {
        PreviewPayWithSheet(payMethodsSelecting("0:usdt"))
    }
}

@Preview(widthDp = 360)
@Composable
private fun PayWithSheetLongPreview() {
    val symbols = listOf("USD₮", "TON", "GRAM", "NOT", "DOGS", "HMSTR", "CATI", "MAJOR", "STON")
    val tokens = symbols.mapIndexed { index, symbol ->
        PayMethodRowUi(
            id = symbol,
            title = symbol,
            caption = "Balance 10",
            imageUrl = null,
            chainBadge = null,
            kind = PayMethodKind.Token,
            selected = index == 0,
        )
    }
    ThemedPreview {
        PreviewPayWithSheet(tokens + payMethodRows.last())
    }
}

@Preview(widthDp = 360)
@Composable
private fun TokenRtlPreview() {
    ThemedPreview(layoutDirection = LayoutDirection.Rtl) {
        PreviewScreen(tokenState)
    }
}

@Preview(widthDp = 360, fontScale = 1.2f)
@Composable
private fun TokenFontScalePreview() {
    ThemedPreview {
        PreviewScreen(tokenState)
    }
}
