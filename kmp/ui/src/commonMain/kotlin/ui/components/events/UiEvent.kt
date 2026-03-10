package ui.components.events

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import ui.UiPosition

@Immutable
sealed class UiEvent(
    open val id: String,
    val contentType: Int
) {

    companion object {

        const val CONTENT_TYPE_OTHER = -1
        const val CONTENT_TYPE_HEADER = 0
        const val CONTENT_TYPE_ITEM = 1
        const val CONTENT_TYPE_LOADER = 2
        const val CONTENT_TYPE_ERROR = 3

        fun textPlain(
            text: String,
            moreButtonText: String
        ) = Item.Action.Text.Plain(
            text = text,
            moreButtonText = moreButtonText
        )

        fun textEncrypted(placeholder: String) = Item.Action.Text.Encrypted(
            placeholder = placeholder
        )

        fun product(
            title: String,
            subtitle: String,
            imageUrl: String,
            type: Item.Action.Product.Type
        ) = Item.Action.Product(
            title = title,
            subtitle = subtitle,
            imageUrl = imageUrl,
            type = type,
        )
    }

    @Immutable
    data class Header(
        override val id: String,
        val title: String
    ): UiEvent(id, CONTENT_TYPE_HEADER)

    @Immutable
    data class Item(
        override val id: String,
        val timestamp: Long,
        val actions: ImmutableList<Action>,
        val filterIds: ImmutableList<Int>,
        val spam: Boolean,
        val progress: Boolean,
    ): UiEvent(id, CONTENT_TYPE_ITEM) {

        fun isMatch(filterId: Int) = filterIds.contains(filterId)

        @Immutable
        data class Action(
            val title: String,
            val subtitle: String,
            val badge: String?,
            val incomingAmount: String? = null,
            val outgoingAmount: String? = null,
            val date: String,
            val imageUrl: String?,
            val iconUrl: String?,
            val product: Product?,
            val text: Text?,
            val state: State,
            val warningText: String?,
            val rightDescription: String?,
            val spam: Boolean,
            val position: UiPosition,
        ) {

            val showDivider: Boolean
                get() = position == UiPosition.Start || position == UiPosition.Middle

            val hasAttachments: Boolean
                get() = !spam && (product != null || text != null)

            enum class State {
                Pending, Success, Failed
            }

            @Immutable
            data class Product(
                val title: String,
                val subtitle: String,
                val imageUrl: String,
                val type: Type
            ) {

                val wrong: Boolean
                    get() = type == Type.Wrong

                val verified: Boolean
                    get() = type == Type.Verified

                enum class Type {
                    Default, Wrong, Verified
                }
            }

            @Immutable
            sealed class Text {

                @Immutable
                data class Plain(
                    val text: String,
                    val moreButtonText: String
                ): Text()

                @Immutable
                data class Encrypted(
                    val placeholder: String
                ): Text()
            }
        }
    }
}

