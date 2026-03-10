package ui.components.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import ui.MASKED_TEXT_VALUE_PLACEHOLDER
import ui.components.base.SimpleText
import ui.components.image.AsyncImage
import ui.theme.Shapes
import ui.theme.UIKit
import ui.theme.resources.Res
import ui.theme.resources.ic_verification_16

private val imageSize = 64.dp

@Composable
internal fun EventActionProduct(
    modifier: Modifier = Modifier,
    product: UiEvent.Item.Action.Product,
    hiddenBalances: Boolean,
    onClick: () -> Unit
) {

    val title = if (hiddenBalances) MASKED_TEXT_VALUE_PLACEHOLDER else product.title
    val subtitle = if (hiddenBalances) MASKED_TEXT_VALUE_PLACEHOLDER else product.subtitle

    Row(
        modifier = modifier
            .height(imageSize)
            .clip(Shapes.medium12)
            .background(UIKit.colorScheme.background.contentTint)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically
    ) {
        EventActionProductIcon(
            imageUrl = product.imageUrl,
            hiddenBalances = hiddenBalances
        )

        EventActionProductTitles(
            title = title,
            subtitle = subtitle,
            subtitleColor = if (product.wrong && !hiddenBalances) UIKit.colorScheme.accent.orange else UIKit.colorScheme.text.secondary,
            verifiedIcon = product.verified && !hiddenBalances
        )
    }
}

@Composable
private fun EventActionProductIcon(
    imageUrl: String,
    hiddenBalances: Boolean
) {
    AsyncImage(
        modifier = Modifier
            .size(imageSize)
            .then(
                if (hiddenBalances) Modifier.blur(16.dp) else Modifier
            ),
        url = imageUrl,
        size = 172,
        crossfadeDuration = 0
    )
}

@Composable
private fun EventActionProductTitles(
    title: String,
    subtitle: String,
    subtitleColor: Color,
    verifiedIcon: Boolean,
) {
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        SimpleText(
            text = title,
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.primary,
        )

        EventActionProductSubtitle(
            text = subtitle,
            color = subtitleColor,
            verifiedIcon = verifiedIcon
        )
    }
}

@Composable
private fun EventActionProductSubtitle(
    text: String,
    color: Color,
    verifiedIcon: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SimpleText(
            text = text,
            style = UIKit.typography.body2,
            color = color,
        )

        if (verifiedIcon) {
            Icon(
                painter = painterResource(Res.drawable.ic_verification_16),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = UIKit.colorScheme.text.secondary
            )
        }
    }
}