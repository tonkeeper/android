package ui.components.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import ui.MASKED_TEXT_VALUE_PLACEHOLDER
import ui.components.base.SimpleText
import ui.theme.UIKit

@Composable
internal fun EventActionAmount(
    incomingAmount: String?,
    outgoingAmount: String?,
    rightDescription: String?,
    date: String,
    spam: Boolean,
    hiddenBalances: Boolean,
) {

    val textColor = if (spam) UIKit.colorScheme.text.tertiary else UIKit.colorScheme.text.primary
    val textIncomingColor = if (spam) UIKit.colorScheme.text.tertiary else UIKit.colorScheme.accent.green

    val rightDescription = rightDescription?.take(18)

    val showIncomingAmount = if (incomingAmount == null) null else {
        if (hiddenBalances) MASKED_TEXT_VALUE_PLACEHOLDER else incomingAmount
    }

    val showOutgoingAmount = if (outgoingAmount == null) null else {
        if (hiddenBalances) MASKED_TEXT_VALUE_PLACEHOLDER else outgoingAmount
    }

    val isEmpty = showIncomingAmount == null && showOutgoingAmount == null

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (isEmpty) {
            SimpleText(
                text = "-",
                style = UIKit.typography.label1,
                color = textColor,
            )
        } else {
            if (showIncomingAmount != null) {
                SimpleText(
                    text = showIncomingAmount,
                    style = UIKit.typography.label1,
                    color = textIncomingColor,
                )
            }

            if (showOutgoingAmount != null) {
                SimpleText(
                    text = showOutgoingAmount,
                    style = UIKit.typography.label1,
                    color = textColor,
                )
            }
        }

        SimpleText(
            text = date,
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.secondary,
        )

        if (rightDescription != null) {
            SimpleText(
                text = rightDescription,
                style = UIKit.typography.body3,
                color = UIKit.colorScheme.accent.orange,
            )
        }
    }
}