package com.tonapps.signer.screen.sign

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.signer.core.repository.KeyRepository
import com.tonapps.signer.extensions.base64
import com.tonapps.signer.extensions.parseCellOrEmpty
import com.tonapps.signer.password.Password
import com.tonapps.signer.screen.sign.list.SignItem
import com.tonapps.signer.tlb.JettonTransfer
import com.tonapps.signer.tlb.NftTransfer
import com.tonapps.signer.tlb.StringTlbConstructor
import com.tonapps.signer.vault.SignerVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.bitstring.BitString
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.CommonMsgInfoRelaxed
import org.ton.block.CurrencyCollection
import org.ton.block.Either
import org.ton.block.MessageRelaxed
import org.ton.block.MsgAddressInt
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.CellType
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.loadTlb
import security.vault.safeArea
import uikit.list.ListCell

class SignViewModel(
    private val id: Long,
    private val boc: String,
    private val repository: KeyRepository,
    private val vault: SignerVault,
): ViewModel() {

    val keyEntity = repository.getKey(id).filterNotNull()

    private val _actionsFlow = MutableStateFlow<List<SignItem>?>(null)
    val actionsFlow = _actionsFlow.asStateFlow().filterNotNull()

    private val unsignedBody: Cell = boc.parseCellOrEmpty()

    init {
        viewModelScope.launch {
            _actionsFlow.value = parseBoc()
        }
    }

    fun sign(context: Context) = Password.authenticate(context).safeArea {
        vault.getPrivateKey(it, id)
    }.map {
        sign(it).base64()
    }.flowOn(Dispatchers.IO).take(1)

    private fun sign(privateKey: PrivateKeyEd25519): Cell {
        val data = privateKey.sign(unsignedBody.hash())
        val signature = BitString(data)

        return CellBuilder.createCell {
            storeBits(signature)
            storeBits(unsignedBody.bits)
            storeRefs(unsignedBody.refs)
        }
    }

    private fun parseBoc(): List<SignItem> {
        val items = mutableListOf<SignItem>()
        try {
            val slice = unsignedBody.beginParse()
            val refs = slice.refs
            for ((index, ref) in slice.refs.withIndex()) {
                val position = ListCell.getPosition(refs.size, index)
                if (ref.type != CellType.ORDINARY) {
                    items.add(SignItem.Unknown(position))
                    continue
                }
                val msg = ref.parse { loadTlb(MessageRelaxed.tlbCodec(AnyTlbConstructor)) }
                items.add(parseMessage(msg, position))
            }
        } catch (ignored: Throwable) {}
        if (items.isEmpty()) {
            items.add(SignItem.Unknown(ListCell.Position.SINGLE))
        }
        return items
    }

    private fun parseMessage(msg: MessageRelaxed<Cell>, position: ListCell.Position): SignItem {
        try {
            val info = msg.info as CommonMsgInfoRelaxed.IntMsgInfoRelaxed
            val body = getBody(msg.body)
            val opCode = parseOpCode(body)
            val jettonTransfer = parseJettonTransfer(opCode, body)
            val nftTransfer = parseNftTransfer(opCode, body)

            val value = parseValue(info.value)
            return SignItem.Send(
                target = parseAddress(info.dest),
                value = value,
                comment = parseComment(body, jettonTransfer, nftTransfer),
                position = position,
                value2 = jettonTransfer?.coins?.let {
                    formatCoins(coins = it)
                }
            )
        } catch (e: Throwable) {
            return SignItem.Unknown(position)
        }
    }

    private fun parseJettonTransfer(opCode: Int, cell: Cell?): JettonTransfer? {
        return if (opCode == 0xf8a7ea5) {
            cell?.parse { loadTlb(JettonTransfer.tlbCodec()) }
        } else {
            null
        }
    }

    private fun parseNftTransfer(opCode: Int, cell: Cell?): NftTransfer? {
        return if (opCode == 0x5fcc3d14) {
            cell?.parse { loadTlb(NftTransfer.tlbCodec()) }
        } else {
            null
        }
    }

    private fun getBody(body: Either<Cell, CellRef<Cell>>): Cell? {
        var cell = body.x
        if (cell == null || cell.isEmpty()) {
            cell = body.y?.value
        }
        return cell
    }

    private fun parseOpCode(cell: Cell?): Int {
        val slice = cell?.beginParse() ?: return 0
        val opCode = if (slice.isEmpty()) {
            0
        } else {
            slice.loadUInt32().toInt()
        }
        return opCode
    }

    private fun parseComment(
        cell: Cell?,
        jettonTransfer: JettonTransfer?,
        nftTransfer: NftTransfer?
    ): String? {
        return if (jettonTransfer != null) {
            jettonTransfer.comment
        } else if (nftTransfer != null) {
            nftTransfer.comment
        } else {
            cell?.parse { loadTlb(StringTlbConstructor) }
        }
    }

    private fun parseValue(value: CurrencyCollection): String {
        return formatCoins("TON", value.coins)
    }

    private fun formatCoins(currency: String = "", coins: Coins): String {
        if (currency.isEmpty()) {
            return coins.toString()
        }
        val builder = StringBuilder()
        builder.append(coins)
        builder.append(" ")
        builder.append(currency)
        return builder.toString()
    }

    private fun parseAddress(address: MsgAddressInt): String {
        if (address is AddrStd) {
            return address.toString(userFriendly = true)
        }
        return "none"
    }
}