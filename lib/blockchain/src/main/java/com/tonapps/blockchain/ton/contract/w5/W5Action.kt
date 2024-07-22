package com.tonapps.blockchain.ton.contract.w5

import com.tonapps.blockchain.ton.TonSendMode
import com.tonapps.blockchain.ton.contract.BaseWalletContract.Companion.createIntMsg
import com.tonapps.blockchain.ton.contract.MessageType
import org.ton.block.AddrStd
import org.ton.block.Maybe
import org.ton.block.MessageRelaxed
import org.ton.block.toMaybe
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.CellBuilder.Companion.beginCell
import org.ton.cell.storeRef
import org.ton.contract.wallet.WalletTransfer
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.constructor.tlbCodec
import org.ton.tlb.storeRef
import org.ton.tlb.storeTlb

sealed class W5Action(
    open val type: String
) {

    companion object {

        private fun toSafeV5R1SendMode(sendMode: Int, authType: MessageType): Int {
            if (authType == MessageType.Internal || authType == MessageType.Extension) {
                return sendMode
            }
            return sendMode or TonSendMode.IGNORE_ERRORS.value
        }

        private fun isOutActionExtended(out: W5Action): Boolean {
            return out is OutActionSetIsPublicKeyEnabled || out is OutActionAddExtension || out is OutActionRemoveExtension
        }

        private fun isOutActionBasic(out: W5Action): Boolean {
            return !isOutActionExtended(out)
        }

        private fun storeOutList(actions: List<Out>): (CellBuilder) -> Unit = { builder ->
            val cell = actions.fold(beginCell().endCell()) { cell, action ->
                beginCell()
                    .storeRef(cell)
                    .storeRef(action.store())
                    .endCell()
            }
            builder.storeSlice(cell.beginParse())
        }

        private fun packExtendedActionsRec(extendedActions: List<Out>): Cell {
            var builder = beginCell().storeRef(extendedActions.first().store())
            val rest = extendedActions.drop(1)
            if (rest.isNotEmpty()) {
                builder = builder.storeRef(packExtendedActionsRec(rest))
            }
            return builder.endCell()
        }

        fun storeOutListExtendedV5R1(gifts: List<WalletTransfer>, authType: MessageType): (CellBuilder) -> Unit {
            return storeOutListExtendedV5R1(patchV5R1ActionsSendMode(gifts.map {
                OutActionSendMsg(it)
            }, authType))
        }

        fun storeOutListExtendedV5R1(actions: List<Out>): (CellBuilder) -> Unit = { builder ->
            val extendedActions = actions.filter { isOutActionExtended(it) }
            val basicActions = actions.filter { isOutActionBasic(it) }


            val outListPacked: Maybe<Cell> = if (basicActions.isNotEmpty()) {
                beginCell().storeRef(storeOutList(basicActions.reversed())).endCell()
            } else {
                null
            }.toMaybe()

            builder.storeTlb(Maybe.tlbCodec(Cell.tlbCodec()), outListPacked)

            if (extendedActions.isEmpty()) {
                builder.storeUInt(0, 1)
            } else {
                val rest = extendedActions.drop(1)
                builder.storeUInt(1, 1)
                builder.storeRef(extendedActions.first().store())
                if (rest.isNotEmpty()) {
                    builder.storeRef(packExtendedActionsRec(rest))
                }
            }
        }

        private fun patchV5R1ActionsSendMode(actions: List<Out>, authType: MessageType): List<Out> {
            return actions.map { action ->
                if (action is OutActionSendMsg) {
                    val safeSendMode = toSafeV5R1SendMode(action.message.sendMode, authType)
                    action.copy(message = action.message.copy(sendMode = safeSendMode))
                } else {
                    action
                }
            }
        }
    }

    sealed class Out(
        override val type: String
    ): W5Action(type) {
        open fun store(): (CellBuilder) -> Unit {
            return { }
        }
    }

    data class OutActionAddExtension(
        val address: AddrStd,
    ): Out("addExtension") {
        override fun store(): (CellBuilder) -> Unit = { builder ->
            builder.storeUInt(0x02, 8)
            builder.storeTlb(AddrStd, address)
        }
    }

    data class OutActionRemoveExtension(
        val address: AddrStd,
    ): Out("removeExtension") {
        override fun store(): (CellBuilder) -> Unit = { builder ->
            builder.storeUInt(0x03, 8)
            builder.storeTlb(AddrStd, address)
        }
    }

    data class OutActionSetIsPublicKeyEnabled(
        val isEnabled: Boolean,
    ): Out("setIsPublicKeyEnabled") {
        override fun store(): (CellBuilder) -> Unit = { builder ->
            builder.storeUInt(0x04, 8)
            builder.storeUInt(if (isEnabled) 1 else 0, 1)
        }

    }

    data class OutActionSendMsg(
        val message: WalletTransfer
    ): Out("sendMsg") {

        override fun store(): (CellBuilder) -> Unit = { builder ->
            builder.storeUInt(0x0ec3c86d, 32)
            builder.storeUInt(message.sendMode, 8)
            val intMsg = CellRef(createIntMsg(message))
            builder.storeRef(MessageRelaxed.tlbCodec(AnyTlbConstructor), intMsg)
        }
    }

    data class OutActionSetCode(
        val newCode: Cell
    ): Out("setCode") {

        override fun store(): (CellBuilder) -> Unit = { builder ->
            builder.storeUInt(0xad4de08e, 32)
            builder.storeRef(newCode)
        }
    }

}
