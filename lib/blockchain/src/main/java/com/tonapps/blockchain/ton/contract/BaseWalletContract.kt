package com.tonapps.blockchain.ton.contract

import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.normalizeHash
import com.tonapps.blockchain.ton.extensions.storeMaybeAddress
import com.tonapps.blockchain.ton.extensions.storeMaybeStringTail
import com.tonapps.blockchain.ton.extensions.storeOpCode
import com.tonapps.blockchain.ton.extensions.toAccountId
import org.ton.bitstring.BitString
import org.ton.block.AddrNone
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.CommonMsgInfoRelaxed
import org.ton.block.CurrencyCollection
import org.ton.block.Either
import org.ton.block.ExtInMsgInfo
import org.ton.block.Maybe
import org.ton.block.Message
import org.ton.block.MessageRelaxed
import org.ton.block.MsgAddressExt
import org.ton.block.MsgAddressInt
import org.ton.block.StateInit
import org.ton.block.invoke
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.buildCell
import org.ton.contract.wallet.WalletTransfer
import org.ton.kotlin.crypto.PrivateKeyEd25519
import org.ton.kotlin.crypto.PublicKeyEd25519
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.loadTlb
import org.ton.tlb.storeTlb
import java.math.BigInteger

abstract class BaseWalletContract(
    val workchain: Int = DEFAULT_WORKCHAIN,
    val publicKey: PublicKeyEd25519
) {

    companion object {
        const val DEFAULT_WORKCHAIN = 0
        const val DEFAULT_WALLET_ID: Int = 698983191

        fun create(
            publicKey: PublicKeyEd25519,
            v: String,
            networkGlobalId: Int
        ): BaseWalletContract {
            return when (v.lowercase()) {
                "v3r1" -> WalletV3R1Contract(publicKey = publicKey)
                "v3r2" -> WalletV3R2Contract(publicKey = publicKey)
                "v4r1" -> WalletV4R1Contract(publicKey = publicKey)
                "v4r2" -> WalletV4R2Contract(publicKey = publicKey)
                "v5beta" -> WalletV5BetaContract(
                    publicKey = publicKey,
                    networkGlobalId = networkGlobalId
                )
                "v5r1" -> WalletV5R1Contract(
                    publicKey = publicKey,
                    networkGlobalId = networkGlobalId
                )
                else -> throw IllegalArgumentException("Unsupported contract version: $v")
            }
        }

        fun resolveVersion(
            publicKey: PublicKeyEd25519,
            accountId: String,
            network: TonNetwork
        ): WalletVersion {
            return resolveVersion(publicKey, accountId, network.value)
        }

        fun resolveVersion(
            publicKey: PublicKeyEd25519,
            accountId: String,
            networkGlobalId: Int
        ): WalletVersion {
            val v4r2 = WalletV4R2Contract(publicKey = publicKey).address.toAccountId()
            if (accountId.equalsAddress(v4r2)) {
                return WalletVersion.V4R2
            }
            val v5r1 = WalletV5R1Contract(
                publicKey = publicKey,
                networkGlobalId = networkGlobalId
            ).address.toAccountId()
            if (accountId.equalsAddress(v5r1)) {
                return WalletVersion.V5R1
            }
            val v5beta = WalletV5BetaContract(
                publicKey = publicKey,
                networkGlobalId = networkGlobalId
            ).address.toAccountId()
            if (accountId.equalsAddress(v5beta)) {
                return WalletVersion.V5BETA
            }
            val v4r1 = WalletV4R1Contract(publicKey = publicKey).address.toAccountId()
            if (accountId.equalsAddress(v4r1)) {
                return WalletVersion.V4R1
            }

            val v3r2 = WalletV3R2Contract(publicKey = publicKey).address.toAccountId()
            if (accountId.equalsAddress(v3r2)) {
                return WalletVersion.V3R2
            }

            val v3r1 = WalletV3R1Contract(publicKey = publicKey).address.toAccountId()
            if (accountId.equalsAddress(v3r1)) {
                return WalletVersion.V3R1
            }

            return WalletVersion.UNKNOWN
        }

        fun create(
            publicKey: PublicKeyEd25519,
            v: String,
            network: TonNetwork
        ): BaseWalletContract {
            return create(
                publicKey = publicKey,
                v = v,
                networkGlobalId = network.value
            )
        }

        fun createIntMsg(gift: WalletTransfer): MessageRelaxed<Cell> {
            val info = when (val dest = gift.destination) {
                is MsgAddressInt -> {
                    CommonMsgInfoRelaxed.IntMsgInfoRelaxed(
                        ihrDisabled = true,
                        bounce = gift.bounceable,
                        bounced = false,
                        src = AddrNone,
                        dest = dest,
                        value = gift.coins,
                        ihrFee = Coins(),
                        fwdFee = Coins(),
                        createdLt = 0u,
                        createdAt = 0u
                    )
                }

                is MsgAddressExt -> {
                    CommonMsgInfoRelaxed.ExtOutMsgInfoRelaxed(
                        src = AddrNone,
                        dest = dest,
                        createdLt = 0u,
                        createdAt = 0u
                    )
                }
            }

            val init = Maybe.of(gift.messageData.stateInit?.let {
                Either.of<StateInit, CellRef<StateInit>>(null, it)
            })

            val bodyCell = gift.messageData.body
            val body = if (bodyCell.isEmpty()) {
                Either.of<Cell, CellRef<Cell>>(Cell.empty(), null)
            } else {
                Either.of<Cell, CellRef<Cell>>(null, CellRef(cell = bodyCell, AnyTlbConstructor)) // TODO TONSDK
            }

            return MessageRelaxed(
                info = info,
                init = init,
                body = body,
            )
        }
    }

    val walletId = DEFAULT_WALLET_ID + workchain

    private val stateInit: StateInit by lazy {
        val dataCell = getStateCell()
        val code = getCode()
        StateInit(code, dataCell)
    }

    val stateInitRef: CellRef<StateInit> by lazy {
        CellRef.valueOf(stateInitCell(), StateInit)
    }

    val address: AddrStd by lazy {
        val stateInitRef = CellRef(stateInit, StateInit)
        val hash = stateInitRef.hash()
        AddrStd(workchain, hash)
    }

    fun stateInitCell(): Cell {
        return CellBuilder.createCell {
            storeTlb(StateInit.tlbCodec(), stateInit)
        }
    }

    abstract val features: WalletFeature

    abstract val maxMessages: Int

    fun isSupportedFeature(feature: WalletFeature): Boolean {
        return features.contains(feature)
    }

    abstract fun getStateCell(): Cell

    abstract fun getCode(): Cell

    abstract fun getWalletVersion(): WalletVersion

    abstract fun removePlugin(
        seqNo: Int,
        validUntil: Long,
        queryId: BigInteger,
        forwardAmount: Coins,
        pluginAddress: AddrStd
    ): Cell

    abstract fun createTransferUnsignedBody(
        validUntil: Long,
        seqNo: Int,
        internalMessage: Boolean = false,
        queryId: BigInteger? = null,
        vararg gifts: WalletTransfer
    ): Cell

    private fun signBody(
        privateKey: PrivateKeyEd25519,
        unsignedBody: Cell,
    ): Cell {
        val hash = unsignedBody.hash().toByteArray()
        val signature = BitString(privateKey.signToByteArray(hash))
        return signedBody(signature, unsignedBody)
    }

    open fun signedBody(
        signature: BitString,
        unsignedBody: Cell,
    ) = CellBuilder.createCell {
        storeBits(signature)
        storeBits(unsignedBody.bits)
        storeRefs(unsignedBody.refs)
    }

    fun normalizedHashFromSignedBody(signedBody: Cell): BitString? {
        try {
            val message = parseTransferMessageCell(signedBody)
            return message.normalizeHash()
        } catch (e: Throwable) {
            return null
        }
    }

    fun createTransferMessageCell(
        address: MsgAddressInt,
        privateKey: PrivateKeyEd25519,
        seqNo: Int,
        unsignedBody: Cell
    ): Cell {
        val message = createTransferMessage(address, privateKey, seqNo, unsignedBody)

        return buildCell {
            storeTlb(Message.tlbCodec(AnyTlbConstructor), message)
        }
    }

    fun createTransferMessage(
        address: MsgAddressInt,
        privateKey: PrivateKeyEd25519,
        seqno: Int,
        unsignedBody: Cell
    ): Message<Cell> {
        val info = ExtInMsgInfo(
            src = AddrNone,
            dest = address,
            importFee = Coins()
        )

        val init = if (seqno == 0) {
            stateInit
        } else null

        val maybeStateInit =
            Maybe.of(init?.let { Either.of<StateInit, CellRef<StateInit>>(null, CellRef(value = it, StateInit)) })

        val transferBody = signBody(privateKey, unsignedBody)

        val body = Either.of<Cell, CellRef<Cell>>(null, CellRef(cell = transferBody, AnyTlbConstructor))
        return Message(
            info = info,
            init = maybeStateInit,
            body = body
        )
    }

    fun createTransferMessage(
        address: MsgAddressInt,
        seqno: Int,
        transferBody: Cell
    ): Message<Cell> {
        val info = ExtInMsgInfo(
            src = AddrNone,
            dest = address,
            importFee = Coins()
        )

        val init = if (seqno == 0) {
            stateInit
        } else null

        val maybeStateInit =
            Maybe.of(init?.let { Either.of<StateInit, CellRef<StateInit>>(null, CellRef(value = it, StateInit)) })

        val body = Either.of<Cell, CellRef<Cell>>(null, CellRef(cell = transferBody, AnyTlbConstructor))
        return Message(
            info = info,
            init = maybeStateInit,
            body = body
        )
    }

    fun parseTransferMessageCell(cell: Cell) = cell.parse {
        loadTlb(Message.tlbCodec(AnyTlbConstructor))
    }

    fun createTransferMessageCell(
        address: MsgAddressInt,
        seqno: Int,
        transferBody: Cell
    ): Cell {
        val message = createTransferMessage(address, seqno, transferBody)

        val cell = buildCell {
            storeTlb(Message.tlbCodec(AnyTlbConstructor), message)
        }
        return cell
    }

    /**
     * Wraps an already signed `internal_signed` body into an internal message addressed to this
     * wallet, the shape a relayer re-wraps into its own external message (TON Connect `signMessage`).
     * The state init rides along only while the wallet is undeployed, as for an external transfer.
     */
    fun createSignedInternalMessage(
        seqno: Int,
        signedBody: Cell
    ): MessageRelaxed<Cell> {
        val info = CommonMsgInfoRelaxed.IntMsgInfoRelaxed(
            ihrDisabled = true,
            bounce = false,
            bounced = false,
            src = AddrNone,
            dest = address,
            value = CurrencyCollection.ZERO,
            ihrFee = Coins(),
            fwdFee = Coins(),
            createdLt = 0u,
            createdAt = 0u
        )

        val init = if (seqno == 0) {
            stateInit
        } else {
            null
        }

        val maybeStateInit =
            Maybe.of(init?.let { Either.of<StateInit, CellRef<StateInit>>(null, CellRef(value = it, StateInit)) })

        val body = Either.of<Cell, CellRef<Cell>>(null, CellRef(cell = signedBody, AnyTlbConstructor))
        return MessageRelaxed(
            info = info,
            init = maybeStateInit,
            body = body
        )
    }

    fun createSignedInternalMessageCell(
        seqno: Int,
        signedBody: Cell
    ): Cell {
        val message = createSignedInternalMessage(seqno, signedBody)
        return buildCell {
            storeTlb(MessageRelaxed.tlbCodec(AnyTlbConstructor), message)
        }
    }

    fun createBatteryBody(
        address: MsgAddressInt? = null,
        appliedPromo: String? = null
    ): Cell {
        return buildCell {
            storeOpCode(TONOpCode.BATTERY_PAYLOAD)
            storeMaybeAddress(address)
            storeMaybeStringTail(appliedPromo)
        }
    }

    fun getOutMsgs(vararg gifts: WalletTransfer): List<Cell> {
        val outMsgs = mutableListOf<Cell>()
        for (gift in gifts) {
            outMsgs.add(
                MessageRelaxed.tlbCodec(AnyTlbConstructor).createCell(createIntMsg(gift))
            )
        }
        return outMsgs
    }
}
