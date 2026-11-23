package com.tonapps.deposit.multicoin.screens.buy

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonapps.deposit.multicoin.domain.RampSavedCard
import com.tonapps.uikit.icon.UIKitIcon
import ui.theme.UIKit

// What a saved card's icon shows: the brand's logo, or its name written out, and the last digits.
data class BuyCryptoCard(val logo: Logo, val last4: String?) {

    sealed interface Logo {
        data object Visa : Logo
        data object Mastercard : Logo
        data class Name(val name: String) : Logo
    }
}

internal fun RampSavedCard.icon(): BuyCryptoCard {
    val logo = when (brand?.lowercase()) {
        VISA -> BuyCryptoCard.Logo.Visa
        MASTERCARD -> BuyCryptoCard.Logo.Mastercard
        else -> BuyCryptoCard.Logo.Name(brand?.uppercase().orEmpty())
    }
    return BuyCryptoCard(logo, last4)
}

@Composable
fun BuyCryptoCardIcon(card: BuyCryptoCard, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 30.dp)
            .background(UIKit.colorScheme.accent.blue, shape)
            .border(0.5.dp, CARD_STROKE, shape),
    ) {
        when (val logo = card.logo) {
            BuyCryptoCard.Logo.Visa -> Image(
                painter = painterResource(UIKitIcon.ic_card_visa),
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopEnd),
            )
            BuyCryptoCard.Logo.Mastercard -> Image(
                painter = painterResource(UIKitIcon.ic_card_mastercard),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 5.dp),
            )
            is BuyCryptoCard.Logo.Name -> Text(
                text = logo.name,
                color = Color.White,
                fontSize = with(LocalDensity.current) { 6.dp.toSp() },
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 5.dp),
            )
        }
        card.last4?.let { last4 ->
            val size = with(LocalDensity.current) { 7.dp.toSp() }
            Text(
                text = last4,
                color = Color.White,
                style = TextStyle(
                    fontSize = size,
                    lineHeight = size,
                    fontWeight = FontWeight.Bold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 4.dp, bottom = 4.dp),
            )
        }
    }
}

private const val VISA = "visa"
private const val MASTERCARD = "mastercard"
private val CARD_STROKE = Color(0x14C2DAFF)