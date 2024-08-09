package com.tonapps.blockchain.ton.tlb

import org.ton.api.pub.PublicKey
import org.ton.block.StateInit
import org.ton.cell.Cell
import org.ton.tlb.CellRef

sealed interface MessageData {
    val body: Cell
    val stateInit: CellRef<StateInit>?

    data class Raw(
        override val body: Cell,
        override val stateInit: CellRef<StateInit>?
    ) : MessageData

    data class Text(
        val text: CellRef<MessageText>
    ) : MessageData {
        constructor(text: MessageText) : this(CellRef(text, MessageText))

        override val body: Cell get() = text.toCell(MessageText)
        override val stateInit: CellRef<StateInit>? get() = null
    }

    companion object {
        @JvmStatic
        fun raw(body: Cell, stateInit: CellRef<StateInit>? = null): Raw =
            Raw(body, stateInit)

        @JvmStatic
        fun text(text: String): Text = Text(
            MessageText.Raw(text)
        )

        @JvmStatic
        fun encryptedText(publicKey: PublicKey, text: String): Text = Text(
            MessageText.Raw(text).encrypt(publicKey)
        )
    }
}