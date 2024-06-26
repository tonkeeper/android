package com.tonapps.signer.screen.sign

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.ton.tlb.JettonTransfer
import com.tonapps.blockchain.ton.tlb.NftTransfer
import com.tonapps.blockchain.ton.tlb.StringTlbConstructor
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.signer.core.repository.KeyRepository
import com.tonapps.signer.password.Password
import com.tonapps.signer.screen.sign.list.SignItem
import com.tonapps.signer.vault.SignerVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.CommonMsgInfoRelaxed
import org.ton.block.CurrencyCollection
import org.ton.block.Either
import org.ton.block.MessageRelaxed
import org.ton.block.MsgAddressInt
import org.ton.cell.Cell
import org.ton.cell.CellType
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.loadTlb
import com.tonapps.security.vault.safeArea
import com.tonapps.uikit.list.ListCell

class SignViewModel(
    private val id: Long,
    private val unsignedBody: Cell,
    private val v: String,
    private val seqno: Int,
    private val network: TonNetwork,
    private val repository: KeyRepository,
    private val vault: SignerVault,
): ViewModel() {

    val keyEntity = repository.getKey(id).filterNotNull()

    private val _actionsFlow = MutableStateFlow<List<SignItem>?>(null)
    val actionsFlow = _actionsFlow.asStateFlow().filterNotNull()

    init {
        viewModelScope.launch {
            _actionsFlow.value = parseBoc()
        }
    }

    fun sign(context: Context) = Password.authenticate(context).safeArea {
        vault.getPrivateKey(it, id)
    }.map {
        sign(it)
    }.flowOn(Dispatchers.IO).take(1)

    fun openEmulate() = keyEntity.map {
        val contract = BaseWalletContract.create(it.publicKey, v, network.value)
        val cell = contract.createTransferMessageCell(contract.address, EmptyPrivateKeyEd25519, seqno, unsignedBody)
        cell.hex()
    }.flowOn(Dispatchers.IO).take(1)

    private fun sign(privateKey: PrivateKeyEd25519): ByteArray {
        return privateKey.sign(unsignedBody.hash())
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
                val item = parseMessage(msg, position)
                items.add(item)
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

            val target = if (nftTransfer != null) {
                nftTransfer.newOwnerAddress
            } else if (jettonTransfer != null) {
                jettonTransfer.toAddress
            } else {
                info.dest
            }

            val value = if (nftTransfer != null) {
                "NFT"
            } else if (jettonTransfer != null) {
                "TOKEN"
            } else {
                parseValue(info.value)
            }
            val value2 = if (nftTransfer != null || jettonTransfer != null) {
                parseAddress(info.dest)
            } else {
                null
            }

            return SignItem.Send(
                target = parseAddress(target, false),
                value = value,
                comment = parseComment(body, jettonTransfer, nftTransfer),
                position = position,
                value2 = value2,
                extra = nftTransfer != null || jettonTransfer != null
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
        val string = if (jettonTransfer != null) {
            jettonTransfer.comment
        } else if (nftTransfer != null) {
            nftTransfer.comment
        } else {
            cell?.parse { loadTlb(StringTlbConstructor) }
        }
        if (string != null) {
            return string
        }
        return null
    }

    private fun parseValue(value: CurrencyCollection): String {
        return formatCoins(value.coins)
    }

    private fun formatCoins(coins: Coins): String {
        val value = coins.amount.toLong() / 1000000000L.toDouble()
        return CurrencyFormatter.format("TON", value, 9).toString()
    }

    private fun parseAddress(address: MsgAddressInt, bounceable: Boolean = true): String {
        if (address is AddrStd) {
            return address.toString(userFriendly = true, bounceable = bounceable)
        }
        return "none"
    }
}