package ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.collections.immutable.persistentListOf
import ui.UiPosition
import ui.components.events.EventHeader
import ui.components.events.EventItem
import ui.components.events.UiEvent

@Preview
@Composable
fun EventHeaderPreview() {
    ThemedPreview {
        EventHeader(text = "Today")
    }
}

@Preview
@Composable
fun EventItemSinglePreview() {
    ThemedPreview {
        EventItem(
            event = UiEvent.Item(
                id = "1",
                timestamp = 0L,
                actions = persistentListOf(
                    UiEvent.Item.Action(
                        title = "Sent",
                        subtitle = "UQBX...a1b2",
                        badge = null,
                        outgoingAmount = "−1.5 TON",
                        incomingAmount = null,
                        date = "12:34",
                        imageUrl = null,
                        iconUrl = null,
                        product = null,
                        text = null,
                        state = UiEvent.Item.Action.State.Success,
                        warningText = null,
                        rightDescription = null,
                        spam = false,
                        position = UiPosition.Single,
                    )
                ),
                filterIds = persistentListOf(),
                spam = false,
                progress = false,
            ),
            hiddenBalances = false,
            onClick = { _, _ -> }
        )
    }
}

@Preview
@Composable
fun EventItemWithBadgePreview() {
    ThemedPreview {
        EventItem(
            event = UiEvent.Item(
                id = "2",
                timestamp = 0L,
                actions = persistentListOf(
                    UiEvent.Item.Action(
                        title = "Swap",
                        subtitle = "Tonkeeper",
                        badge = "DeFi",
                        incomingAmount = "+100 USDT",
                        outgoingAmount = "−50 TON",
                        date = "09:15",
                        imageUrl = null,
                        iconUrl = null,
                        product = null,
                        text = null,
                        state = UiEvent.Item.Action.State.Success,
                        warningText = null,
                        rightDescription = null,
                        spam = false,
                        position = UiPosition.Single,
                    )
                ),
                filterIds = persistentListOf(),
                spam = false,
                progress = false,
            ),
            hiddenBalances = false,
            onClick = { _, _ -> }
        )
    }
}

@Preview
@Composable
fun EventItemPendingPreview() {
    ThemedPreview {
        EventItem(
            event = UiEvent.Item(
                id = "3",
                timestamp = 0L,
                actions = persistentListOf(
                    UiEvent.Item.Action(
                        title = "Sent",
                        subtitle = "UQBX...c3d4",
                        badge = null,
                        outgoingAmount = "−2.0 TON",
                        incomingAmount = null,
                        date = "Just now",
                        imageUrl = null,
                        iconUrl = null,
                        product = null,
                        text = null,
                        state = UiEvent.Item.Action.State.Pending,
                        warningText = null,
                        rightDescription = "Pending",
                        spam = false,
                        position = UiPosition.Single,
                    )
                ),
                filterIds = persistentListOf(),
                spam = false,
                progress = false,
            ),
            hiddenBalances = false,
            onClick = { _, _ -> }
        )
    }
}

@Preview
@Composable
fun EventItemFailedPreview() {
    ThemedPreview {
        EventItem(
            event = UiEvent.Item(
                id = "4",
                timestamp = 0L,
                actions = persistentListOf(
                    UiEvent.Item.Action(
                        title = "Sent",
                        subtitle = "UQBX...e5f6",
                        badge = null,
                        outgoingAmount = "−3.0 TON",
                        incomingAmount = null,
                        date = "Yesterday",
                        imageUrl = null,
                        iconUrl = null,
                        product = null,
                        text = null,
                        state = UiEvent.Item.Action.State.Failed,
                        warningText = "Transaction failed",
                        rightDescription = null,
                        spam = false,
                        position = UiPosition.Single,
                    )
                ),
                filterIds = persistentListOf(),
                spam = false,
                progress = false,
            ),
            hiddenBalances = false,
            onClick = { _, _ -> }
        )
    }
}

@Preview
@Composable
fun EventItemWithTextPreview() {
    ThemedPreview {
        EventItem(
            event = UiEvent.Item(
                id = "5",
                timestamp = 0L,
                actions = persistentListOf(
                    UiEvent.Item.Action(
                        title = "Received",
                        subtitle = "UQBX...g7h8",
                        badge = null,
                        incomingAmount = "+5.0 TON",
                        outgoingAmount = null,
                        date = "10:00",
                        imageUrl = null,
                        iconUrl = null,
                        product = null,
                        text = UiEvent.textPlain(
                            text = "Payment for services rendered",
                            moreButtonText = "More"
                        ),
                        state = UiEvent.Item.Action.State.Success,
                        warningText = null,
                        rightDescription = null,
                        spam = false,
                        position = UiPosition.Single,
                    )
                ),
                filterIds = persistentListOf(),
                spam = false,
                progress = false,
            ),
            hiddenBalances = false,
            onClick = { _, _ -> }
        )
    }
}

@Preview
@Composable
fun EventItemMultiActionPreview() {
    ThemedPreview {
        EventItem(
            event = UiEvent.Item(
                id = "6",
                timestamp = 0L,
                actions = persistentListOf(
                    UiEvent.Item.Action(
                        title = "Sent",
                        subtitle = "UQBX...i9j0",
                        badge = null,
                        outgoingAmount = "−1.0 TON",
                        incomingAmount = null,
                        date = "14:20",
                        imageUrl = null,
                        iconUrl = null,
                        product = null,
                        text = null,
                        state = UiEvent.Item.Action.State.Success,
                        warningText = null,
                        rightDescription = null,
                        spam = false,
                        position = UiPosition.Start,
                    ),
                    UiEvent.Item.Action(
                        title = "Received",
                        subtitle = "UQBX...k1l2",
                        badge = null,
                        incomingAmount = "+50 USDT",
                        outgoingAmount = null,
                        date = "14:20",
                        imageUrl = null,
                        iconUrl = null,
                        product = null,
                        text = null,
                        state = UiEvent.Item.Action.State.Success,
                        warningText = null,
                        rightDescription = null,
                        spam = false,
                        position = UiPosition.End,
                    )
                ),
                filterIds = persistentListOf(),
                spam = false,
                progress = false,
            ),
            hiddenBalances = false,
            onClick = { _, _ -> }
        )
    }
}

@Preview
@Composable
fun EventItemHiddenBalancesPreview() {
    ThemedPreview {
        EventItem(
            event = UiEvent.Item(
                id = "7",
                timestamp = 0L,
                actions = persistentListOf(
                    UiEvent.Item.Action(
                        title = "Received",
                        subtitle = "UQBX...m3n4",
                        badge = null,
                        incomingAmount = "+10.0 TON",
                        outgoingAmount = null,
                        date = "08:45",
                        imageUrl = null,
                        iconUrl = null,
                        product = null,
                        text = null,
                        state = UiEvent.Item.Action.State.Success,
                        warningText = null,
                        rightDescription = null,
                        spam = false,
                        position = UiPosition.Single,
                    )
                ),
                filterIds = persistentListOf(),
                spam = false,
                progress = false,
            ),
            hiddenBalances = true,
            onClick = { _, _ -> }
        )
    }
}
