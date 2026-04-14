package ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ui.components.details.TKDetails
import ui.components.details.TKDetailsInfo
import ui.components.details.TKDetailsLine
import ui.components.details.TKDetailsRow
import ui.components.details.UiDetails

@Preview
@Composable
fun TKDetailsPreview() {
    ThemedPreview {
        TKDetails(
            details = UiDetails(
                rows = listOf(
                    UiDetails.Row(id = "1", key = "Recipient", value = "UQBx...a1b2"),
                    UiDetails.Row(id = "2", key = "Amount", value = "1.5 TON"),
                    UiDetails.Row(id = "3", key = "Fee", value = "0.005 TON"),
                )
            )
        )
    }
}

@Preview
@Composable
fun TKDetailsInfoPreview() {
    ThemedPreview {
        TKDetailsInfo(
            aboveTitle = "Sent",
            title = "−1.5 TON",
            subtitle = "Tonkeeper",
            verifiedSubtitle = true,
            date = "Today, 12:34",
            failedText = null
        )
    }
}

@Preview
@Composable
fun TKDetailsInfoFailedPreview() {
    ThemedPreview {
        TKDetailsInfo(
            aboveTitle = null,
            title = "−2.0 TON",
            subtitle = "Unknown",
            verifiedSubtitle = false,
            date = "Yesterday, 09:15",
            failedText = "Transaction failed"
        )
    }
}

@Preview
@Composable
fun TKDetailsRowPreview() {
    ThemedPreview {
        TKDetailsRow(
            key = "Recipient",
            value = "UQBx...a1b2",
            iconLeft = null,
            secondaryValue = "≈ \$3.25",
            spoiler = false
        )
    }
}

@Preview
@Composable
fun TKDetailsRowSpoilerPreview() {
    ThemedPreview {
        TKDetailsRow(
            key = "Amount",
            value = "1.5 TON",
            iconLeft = null,
            secondaryValue = null,
            spoiler = true
        )
    }
}

@Preview
@Composable
fun TKDetailsLineShortPreview() {
    ThemedPreview {
        TKDetailsLine(
            key = "Fee",
            value = "0.005 TON",
            iconLeft = null,
            spoiler = false
        )
    }
}

@Preview
@Composable
fun TKDetailsLineLongPreview() {
    ThemedPreview {
        TKDetailsLine(
            key = "Comment",
            value = "This is a long value that exceeds twenty four characters",
            iconLeft = null,
            spoiler = false
        )
    }
}
