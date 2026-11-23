package com.tonapps.signer.screen.sign.body

import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.loadCoins
import com.tonapps.blockchain.ton.tlb.JettonTransfer
import com.tonapps.blockchain.ton.tlb.NftTransfer
import kotlinx.io.bytestring.decodeToString
import org.ton.block.AddrStd
import org.ton.block.CommonMsgInfoRelaxed
import org.ton.block.MessageRelaxed
import org.ton.block.MsgAddressInt
import org.ton.cell.Cell
import org.ton.cell.CellSlice
import org.ton.cell.CellType
import org.ton.contract.CellStringTlbConstructor
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.loadTlb

class SignBodyException(message: String) : IllegalArgumentException(message)

data class SignBody(
    val version: WalletVersion,
    val actions: List<SignAction>,
)

object SignBodyParser {

    private val DETECTION_ORDER = listOf(
        WalletVersion.V5R1,
        WalletVersion.V5BETA,
        WalletVersion.V4R2,
        WalletVersion.V3R2,
    )

    fun parse(body: Cell, v: String): SignBody {
        val requestedVersion = requestedVersion(v)
        if (requestedVersion != null) {
            return parseOrNull(body, requestedVersion)
                ?: throw SignBodyException("Body does not match wallet $requestedVersion")
        }
        return DETECTION_ORDER.firstNotNullOfOrNull { version -> parseOrNull(body, version) }
            ?: throw SignBodyException("Body does not match any wallet version")
    }

    private fun parseOrNull(body: Cell, version: WalletVersion): SignBody? {
        return try {
            SignBody(version, parse(body, version))
        } catch (_: Exception) {
            null
        }
    }

    private fun parse(body: Cell, version: WalletVersion): List<SignAction> {
        val actions = when (version) {
            WalletVersion.V5R1 -> parseV5R1(body)
            WalletVersion.V5BETA -> parseV5Beta(body)
            WalletVersion.V4R1, WalletVersion.V4R2 -> parseV4(body)
            WalletVersion.V3R1, WalletVersion.V3R2 -> parseV3(body)
            WalletVersion.WALLET_TG, WalletVersion.UNKNOWN -> throw SignBodyException("Unsupported wallet version $version")
        }
        if (actions.isEmpty()) {
            throw SignBodyException("Body has no actions")
        }
        return actions
    }

    fun requestedVersion(v: String): WalletVersion? {
        return when (v.lowercase()) {
            "v3r1" -> WalletVersion.V3R1
            "v3r2" -> WalletVersion.V3R2
            "v4r1" -> WalletVersion.V4R1
            "v4r2" -> WalletVersion.V4R2
            "v5beta", "w5 beta" -> WalletVersion.V5BETA
            "v5r1", "w5" -> WalletVersion.V5R1
            else -> null
        }
    }

    private fun parseV5R1(body: Cell): List<SignAction> {
        val slice = body.ordinarySlice()
        slice.requireSignedRequestOpCode()
        slice.skipBits(V5R1_WALLET_ID_BITS + VALID_UNTIL_BITS + SEQNO_BITS)
        val actions = mutableListOf<SignAction>()
        if (slice.loadBoolean()) {
            actions.addAll(parseOutList(slice.loadRef()))
        }
        if (slice.loadBoolean()) {
            actions.addAll(parseExtendedActions(slice))
        }
        slice.requireFullyRead()
        return actions
    }

    private fun parseV5Beta(body: Cell): List<SignAction> {
        val slice = body.ordinarySlice()
        slice.requireSignedRequestOpCode()
        slice.skipBits(V5BETA_WALLET_ID_BITS + VALID_UNTIL_BITS + SEQNO_BITS)
        if (slice.loadBoolean()) {
            throw SignBodyException("W5 beta extended actions are not supported")
        }
        val actions = parseOutList(slice.loadRef())
        slice.requireFullyRead()
        return actions
    }

    private fun parseV4(body: Cell): List<SignAction> {
        val slice = body.ordinarySlice()
        slice.skipBits(LEGACY_HEADER_BITS)
        return when (val op = slice.loadUInt(8).toInt()) {
            V4_OP_SEND -> parseLegacyMessages(slice)
            V4_OP_DEPLOY_AND_INSTALL_PLUGIN -> {
                val workchain = slice.loadInt(8).toInt()
                val balance = slice.loadCoins()
                val stateInit = slice.loadRef()
                slice.loadRef()
                slice.requireFullyRead()
                listOf(SignAction.InstallPlugin(AddrStd(workchain, stateInit.hash()), balance))
            }
            V4_OP_INSTALL_PLUGIN -> {
                val address = slice.loadPluginAddress()
                val amount = slice.loadCoins()
                slice.requirePluginQueryIdTail()
                listOf(SignAction.InstallPlugin(address, amount))
            }
            V4_OP_REMOVE_PLUGIN -> {
                val address = slice.loadPluginAddress()
                val amount = slice.loadCoins()
                slice.requirePluginQueryIdTail()
                listOf(SignAction.RemovePlugin(address, amount))
            }
            else -> throw SignBodyException("Unknown wallet v4 op $op")
        }
    }

    private fun parseV3(body: Cell): List<SignAction> {
        val slice = body.ordinarySlice()
        slice.skipBits(LEGACY_HEADER_BITS)
        return parseLegacyMessages(slice)
    }

    private fun parseLegacyMessages(slice: CellSlice): List<SignAction> {
        val actions = mutableListOf<SignAction>()
        while (slice.remainingRefs > 0) {
            val sendMode = slice.loadUInt(8).toInt()
            actions.add(parseMessage(slice.loadRef(), sendMode))
        }
        slice.requireFullyRead()
        return actions
    }

    private fun parseOutList(list: Cell): List<SignAction> {
        val actions = mutableListOf<SignAction>()
        var node = list.ordinarySlice()
        while (node.remainingBits > 0 || node.remainingRefs > 0) {
            val prev = node.loadRef()
            val tag = node.loadUInt(32).toLong()
            if (tag != TONOpCode.OUT_ACTION_SEND_MSG_TAG.code) {
                throw SignBodyException("Unknown out action $tag")
            }
            val sendMode = node.loadUInt(8).toInt()
            val message = node.loadRef()
            node.requireFullyRead()
            actions.add(parseMessage(message, sendMode))
            node = prev.ordinarySlice()
        }
        return actions.asReversed()
    }

    private fun parseExtendedActions(root: CellSlice): List<SignAction> {
        val actions = mutableListOf<SignAction>()
        var node = root
        while (true) {
            actions.add(parseExtendedAction(node))
            if (node.remainingRefs == 0) {
                break
            }
            val next = node.loadRef()
            node.requireFullyRead()
            node = next.ordinarySlice()
        }
        node.requireFullyRead()
        return actions
    }

    private fun parseExtendedAction(slice: CellSlice): SignAction {
        return when (val tag = slice.loadUInt(8).toInt()) {
            EXTENDED_ACTION_ADD_EXTENSION -> SignAction.AddExtension(slice.loadStdAddress())
            EXTENDED_ACTION_DELETE_EXTENSION -> SignAction.RemoveExtension(slice.loadStdAddress())
            EXTENDED_ACTION_SET_SIGNATURE_AUTH -> SignAction.SetSignatureAuth(slice.loadBoolean())
            else -> throw SignBodyException("Unknown extended action $tag")
        }
    }

    private fun parseMessage(cell: Cell, sendMode: Int): SignAction.Send {
        val slice = cell.ordinarySlice()
        val message = slice.loadTlb(MessageRelaxed.tlbCodec(AnyTlbConstructor))
        slice.requireFullyRead()
        val info = message.info as? CommonMsgInfoRelaxed.IntMsgInfoRelaxed
            ?: throw SignBodyException("Only internal messages can be sent")
        if (info.value.other.isNotEmpty()) {
            throw SignBodyException("Extra currencies can't be shown")
        }
        val body = message.body.x?.takeUnless { it.isEmpty() } ?: message.body.y?.cell
        val opCode = body?.opCode()
        return SignAction.Send(
            destination = info.dest,
            value = info.value.coins,
            sendMode = sendMode,
            payload = parsePayload(opCode, body),
            comment = parseComment(opCode, body),
        )
    }

    private fun parsePayload(opCode: Long?, body: Cell?): SendPayload {
        if (body == null || opCode == null || opCode == TEXT_COMMENT_OP_CODE) {
            return SendPayload.Ton
        }
        return when (opCode) {
            TONOpCode.JETTON_TRANSFER.code -> parseJettonTransfer(body)?.let {
                SendPayload.Jetton(recipient = it.toAddress, excess = it.responseAddress)
            }
            TONOpCode.NFT_TRANSFER.code -> parseNftTransfer(body)?.let {
                SendPayload.Nft(newOwner = it.newOwnerAddress, excess = it.excessesAddress)
            }
            else -> null
        } ?: SendPayload.Unknown(opCode)
    }

    private fun parseComment(opCode: Long?, body: Cell?): String? {
        if (body == null) {
            return null
        }
        val comment = when (opCode) {
            TEXT_COMMENT_OP_CODE -> parseTextComment(body)
            TONOpCode.JETTON_TRANSFER.code -> parseJettonTransfer(body)?.comment
            TONOpCode.NFT_TRANSFER.code -> parseNftTransfer(body)?.comment
            else -> null
        }
        return comment?.trim('\u0000')?.takeIf { it.isNotBlank() }
    }

    private fun parseTextComment(body: Cell): String? {
        return try {
            val slice = body.ordinarySlice()
            slice.skipBits(32)
            slice.loadTlb(CellStringTlbConstructor).decodeToString()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseJettonTransfer(body: Cell): JettonTransfer? {
        return try {
            body.ordinarySlice().loadTlb(JettonTransfer.tlbCodec())
        } catch (_: Exception) {
            null
        }
    }

    private fun parseNftTransfer(body: Cell): NftTransfer? {
        return try {
            body.ordinarySlice().loadTlb(NftTransfer.tlbCodec())
        } catch (_: Exception) {
            null
        }
    }

    private fun Cell.opCode(): Long? {
        val slice = ordinarySlice()
        if (slice.remainingBits < 32) {
            return null
        }
        return slice.loadUInt(32).toLong()
    }

    private fun Cell.ordinarySlice(): CellSlice {
        if (type != CellType.ORDINARY) {
            throw SignBodyException("Exotic cell $type")
        }
        return beginParse()
    }

    private fun CellSlice.requireFullyRead() {
        if (remainingBits != 0 || remainingRefs != 0) {
            throw SignBodyException("Unparsed data: $remainingBits bits, $remainingRefs refs")
        }
    }

    private fun CellSlice.requireSignedRequestOpCode() {
        val opCode = loadUInt(32).toLong()
        if (opCode != TONOpCode.SIGNED_EXTERNAL.code && opCode != TONOpCode.SIGNED_INTERNAL.code) {
            throw SignBodyException("Unknown signed request op $opCode")
        }
    }

    private fun CellSlice.requirePluginQueryIdTail() {
        if (remainingRefs != 0 || (remainingBits != 0 && remainingBits != QUERY_ID_BITS)) {
            throw SignBodyException("Unparsed plugin request data")
        }
        skipBits(remainingBits)
    }

    private fun CellSlice.loadPluginAddress(): AddrStd {
        val workchain = loadInt(8).toInt()
        return AddrStd(workchain, loadBitString(256))
    }

    private fun CellSlice.loadStdAddress(): AddrStd {
        val address = loadTlb(MsgAddressInt)
        if (address !is AddrStd || address.anycast.value != null) {
            throw SignBodyException("Extension address must be a standard address")
        }
        return address
    }
}

private const val LEGACY_HEADER_BITS = 96
private const val V5R1_WALLET_ID_BITS = 32
private const val V5BETA_WALLET_ID_BITS = 80
private const val VALID_UNTIL_BITS = 32
private const val SEQNO_BITS = 32
private const val QUERY_ID_BITS = 64
private const val TEXT_COMMENT_OP_CODE = 0L
private const val V4_OP_SEND = 0
private const val V4_OP_DEPLOY_AND_INSTALL_PLUGIN = 1
private const val V4_OP_INSTALL_PLUGIN = 2
private const val V4_OP_REMOVE_PLUGIN = 3
private const val EXTENDED_ACTION_ADD_EXTENSION = 2
private const val EXTENDED_ACTION_DELETE_EXTENSION = 3
private const val EXTENDED_ACTION_SET_SIGNATURE_AUTH = 4
