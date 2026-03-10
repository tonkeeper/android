package com.tonapps.tonkeeper.ui.screen.events.compose.history

import android.content.Context
import com.tonapps.extensions.locale
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.history.iconRes
import com.tonapps.tonkeeper.core.history.nameRes
import com.tonapps.tonkeeper.extensions.externalDrawableUrl
import com.tonapps.tonkeeper.helper.DateHelper
import com.tonapps.tonkeeper.ui.screen.events.compose.history.state.getFilters
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.events.ActionType
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.events.tx.model.TxAction
import com.tonapps.wallet.data.events.tx.model.TxActionBody
import com.tonapps.wallet.data.events.tx.model.TxEvent
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.SpamTransactionState
import com.tonapps.wallet.localization.Localization
import kotlinx.collections.immutable.toImmutableList
import ui.components.events.UiEvent
import ui.uiPosition

internal class TxUiMapper(
    private val context: Context,
    private val wallet: WalletEntity,
    private val eventsRepository: EventsRepository,
    private val settingsRepository: SettingsRepository,
) {

    private val moreButtonText = context.getString(Localization.more)

    fun changeText(item: UiEvent.Item, text: String): UiEvent.Item {
        val index = item.actions.indexOfFirst {
            it.text is UiEvent.Item.Action.Text.Encrypted
        }
        if (index == -1) {
            return item
        }
        val action = item.actions[index].copy(text = UiEvent.Item.Action.Text.Plain(text, moreButtonText))
        val actions = item.actions.toMutableList()
        actions[index] = action
        return item.copy(actions = actions.toImmutableList())
    }

    private fun product(action: TxAction): UiEvent.Item.Action.Product? {
        val product = action.product ?: return null
        var subtitle = product.subtitle
        var type = UiEvent.Item.Action.Product.Type.Default
        if (action.hasUnverifiedNft) {
            subtitle = context.getString(Localization.nft_unverified)
            type = UiEvent.Item.Action.Product.Type.Wrong
        } else if (action.hasVerifiedNft) {
            type = UiEvent.Item.Action.Product.Type.Verified
        }
        return UiEvent.Companion.product(
            title = product.title,
            subtitle = subtitle.ifBlank { context.getString(Localization.unnamed_collection) },
            imageUrl = product.imageUrl,
            type = type,
        )
    }

    private fun text(hash: String, text: TxActionBody.Text): UiEvent.Item.Action.Text? {
        if (text is TxActionBody.Text.Plain) {
            return UiEvent.Companion.textPlain(
                text = text.text,
                moreButtonText = moreButtonText
            )
        } else if (text is TxActionBody.Text.Encrypted) {
            val decryptedComment = eventsRepository.getDecryptedComment(hash)
            return if (decryptedComment.isNullOrBlank()) {
                UiEvent.Companion.textEncrypted(context.getString(Localization.encrypted_comment))
            } else {
                UiEvent.Companion.textPlain(
                    text = decryptedComment,
                    moreButtonText = moreButtonText
                )
            }
        }
        return null
    }

    private fun amount(action: TxAction): Pair<String?, String?> {
        var incomingFormatted = action.incomingFormatted
        var outgoingFormatted = action.outgoingFormatted

        if (action.type == ActionType.Unknown) {
            outgoingFormatted = action.body.value
        } else if (action.type == ActionType.NftSend) {
            outgoingFormatted = context.getString(Localization.nft)
        } else if (action.type == ActionType.NftReceived || action.type == ActionType.NftPurchase) {
            incomingFormatted = context.getString(Localization.nft)
        } else {
            if (!incomingFormatted.isNullOrBlank()) {
                incomingFormatted = CurrencyFormatter.PREFIX_PLUS + incomingFormatted
            }
            if (!outgoingFormatted.isNullOrBlank()) {
                outgoingFormatted = CurrencyFormatter.PREFIX_MINUS + outgoingFormatted
            }
        }
        return incomingFormatted?.toString() to outgoingFormatted?.toString()
    }

    private fun isSpam(event: TxEvent): Boolean {
        val localSpamState = settingsRepository.getSpamStateTransaction(wallet.id, event.hash)
        return if (localSpamState == SpamTransactionState.UNKNOWN) {
            event.spam
        } else localSpamState == SpamTransactionState.SPAM
    }

    private fun state(event: TxEvent, action: TxAction): UiEvent.Item.Action.State {
        return when {
            event.inProgress -> UiEvent.Item.Action.State.Pending
            action.status == TxAction.Status.Failed -> UiEvent.Item.Action.State.Failed
            else -> UiEvent.Item.Action.State.Success
        }
    }

    fun event(event: TxEvent): UiEvent.Item {
        val isSpam = isSpam(event)
        val actions = event.actions.mapIndexed { index, action ->
            val (incomingFormatted, outgoingFormatted) = amount(action)

            val badge = if (event.isTron) context.getString(Localization.trc20) else null
            val state = state(event, action)

            val title = if (isSpam) {
                context.getString(Localization.spam)
            } else action.title ?: context.getString(action.type.nameRes)

            val iconUrl = if (isSpam || action.isFailed) {
                context.externalDrawableUrl(UIKitIcon.ic_exclamationmark_circle_28)
            } else context.externalDrawableUrl(action.type.iconRes)

            UiEvent.Item.Action(
                state = state,
                title = title,
                subtitle = action.subtitle ?: context.getString(Localization.unknown),
                incomingAmount = incomingFormatted,
                outgoingAmount = outgoingFormatted,
                date = DateHelper.formatTransactionTime(event.timestamp.toLong(), context.locale),
                imageUrl = action.imageUrl,
                iconUrl = iconUrl,
                product = product(action),
                badge = badge,
                text = action.text?.let {
                    text(event.hash, it)
                },
                warningText = if (action.isFailed) context.getString(Localization.failed) else null,
                rightDescription = if (action.hasUnverifiedToken) context.getString(Localization.unverified_token) else null,
                spam = isSpam,
                position = uiPosition(index, event.actions.size)
            )
        }

        return UiEvent.Item(
            id = event.id,
            timestamp = event.timestamp.toLong(),
            actions = actions.toImmutableList(),
            filterIds = event.getFilters().map { it.id }.toImmutableList(),
            spam = actions.any { it.spam },
            progress = event.inProgress
        )
    }

}