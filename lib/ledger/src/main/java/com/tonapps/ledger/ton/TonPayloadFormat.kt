package com.tonapps.ledger.ton

import android.os.Parcelable
import com.tonapps.blockchain.ton.extensions.loadRemainingBits
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.cell.Cell
import org.ton.cell.CellSlice
import org.ton.tlb.loadTlb
import java.math.BigInteger

sealed class JettonBurnCustomPayload : Parcelable {
    @Parcelize
    data class CellPayload(val cell: @RawValue Cell) : JettonBurnCustomPayload()

    @Parcelize
    data class ByteArrayPayload(val byteArray: ByteArray) : JettonBurnCustomPayload()
}

data class DNSWalletRecord(
    val address: @RawValue AddrStd,
    val capabilities: DnsChangeCapabilities?
)

sealed class DNSRecord : Parcelable {
    @Parcelize
    data class Wallet(val wallet: @RawValue DNSWalletRecord?) : DNSRecord()

    @Parcelize
    data class Unknown(val key: ByteArray, val value: @RawValue Cell?) : DNSRecord()
}

data class DnsChangeCapabilities(
    var isWallet: Boolean
)

sealed class TonPayloadFormat : Parcelable {

    @Parcelize
    data class Comment(val text: String) : TonPayloadFormat()

    @Parcelize
    data class JettonTransfer(
        val queryId: BigInteger?,
        val coins: @RawValue Coins,
        val receiverAddress: @RawValue AddrStd,
        val excessesAddress: @RawValue AddrStd,
        val customPayload: @RawValue Cell?,
        val forwardAmount: @RawValue Coins,
        val forwardPayload: @RawValue Cell?
    ) : TonPayloadFormat()

    @Parcelize
    data class NftTransfer(
        val queryId: BigInteger?,
        val newOwnerAddress: @RawValue AddrStd,
        val excessesAddress: @RawValue AddrStd,
        val customPayload: @RawValue Cell?,
        val forwardAmount: @RawValue Coins,
        val forwardPayload: @RawValue Cell?
    ) : TonPayloadFormat()

    @Parcelize
    data class JettonBurn(
        val queryId: BigInteger?,
        val coins: @RawValue Coins,
        val responseDestination: @RawValue AddrStd,
        val customPayload: JettonBurnCustomPayload?
    ) : TonPayloadFormat()

    @Parcelize
    data class SingleNominatorWithdraw(
        val queryId: BigInteger?,
        val coins: @RawValue Coins
    ) : TonPayloadFormat()

    @Parcelize
    data class SingleNominatorChangeValidator(
        val queryId: BigInteger?,
        val address: @RawValue AddrStd,
    ) : TonPayloadFormat()

    @Parcelize
    data class AddWhitelist(
        val queryId: BigInteger?,
        val address: @RawValue AddrStd,
    ) : TonPayloadFormat()

    @Parcelize
    data class TonstakersDeposit(
        val queryId: BigInteger?,
        val appId: BigInteger?,
    ) : TonPayloadFormat()

    @Parcelize
    data class VoteForProposal(
        val queryId: BigInteger?,
        val votingAddress: @RawValue AddrStd,
        val expirationDate: BigInteger,
        val vote: Boolean,
        val needConfirmation: Boolean,
    ) : TonPayloadFormat()

    @Parcelize
    data class ChangeDNSRecord(
        val queryId: BigInteger?,
        val record: DNSRecord,
    ) : TonPayloadFormat()

    @Parcelize
    data class TokenBridgePaySwap(
        val queryId: BigInteger?,
        val swapId: ByteArray,
    ) : TonPayloadFormat()

    @Parcelize
    data class Unsafe(
        val message: @RawValue Cell,
    ) : TonPayloadFormat()

    companion object {
        private val dnsWalletKey = byteArrayOf(
            0xe8.toByte(),
            0xd4.toByte(),
            0x40.toByte(),
            0x50.toByte(),
            0x87.toByte(),
            0x3d.toByte(),
            0xba.toByte(),
            0x86.toByte(),
            0x5a.toByte(),
            0xa7.toByte(),
            0xc1.toByte(),
            0x70.toByte(),
            0xab.toByte(),
            0x4c.toByte(),
            0xce.toByte(),
            0x64.toByte(),
            0xd9.toByte(),
            0x08.toByte(),
            0x39.toByte(),
            0xa3.toByte(),
            0x4d.toByte(),
            0xcf.toByte(),
            0xd6.toByte(),
            0xcf.toByte(),
            0x71.toByte(),
            0xd1.toByte(),
            0x4e.toByte(),
            0x02.toByte(),
            0x05.toByte(),
            0x44.toByte(),
            0x3b.toByte(),
            0x1b.toByte()
        )

        private fun normalizeQueryId(queryId: ULong): BigInteger? {
            return if (queryId != 0UL) BigInteger(queryId.toString()) else null
        }

        private fun loadBuffer(s: CellSlice): ByteArray {
            var bytes = s.loadRemainingBits().toByteArray()

            if (s.remainingBits % 8 != 0) {
                throw Error("Invalid string length: ${s.remainingBits}");
            }

            if (s.remainingRefs != 0 && s.remainingRefs != 1) {
                throw Error("invalid number of refs: ${s.remainingRefs}")
            }

            if (s.remainingRefs == 1) {
                bytes += loadBuffer(s.loadRef().beginParse())
            }

            return bytes
        }

        fun fromCell(cell: Cell, options: ParseOptions? = null): TonPayloadFormat? {
            val params = options ?: ParseOptions()

            if (cell.isEmpty()) {
                return null
            }

            val s = cell.beginParse()

            try {
                val op = s.loadUInt32()
                when (op.toInt()) {
                    0 -> {
                        val str = String(loadBuffer(s))
                        s.endParse()
                        if (str.length > 120) {
                            throw Error("Comment must be at most 120 ASCII characters long")
                        }
                        for (c in str) {
                            if (c.code < 0x20 || c.code >= 0x7f) {
                                throw Error("Comment must only contain printable ASCII characters")
                            }
                        }
                        return Comment(str)
                    }

                    0x0f8a7ea5 -> {
                        val queryId = normalizeQueryId(s.loadUInt64())
                        val coins = s.loadTlb(Coins)
                        val receiverAddress = s.loadTlb(AddrStd.tlbCodec())
                        val excessesAddress = s.loadTlb(AddrStd.tlbCodec())
                        val customPayload = if (s.loadBit()) s.loadRef() else null
                        val forwardAmount = s.loadTlb(Coins)
                        val forwardPayload = if (s.loadBit()) s.loadRef() else null
                        s.endParse()

                        return JettonTransfer(
                            queryId,
                            coins,
                            receiverAddress,
                            excessesAddress,
                            customPayload,
                            forwardAmount,
                            forwardPayload
                        )
                    }

                    0x5fcc3d14 -> {
                        val queryId = normalizeQueryId(s.loadUInt64())
                        val newOwnerAddress = s.loadTlb(AddrStd.tlbCodec())
                        val excessesAddress = s.loadTlb(AddrStd.tlbCodec())
                        val customPayload = if (s.loadBit()) s.loadRef() else null
                        val forwardAmount = s.loadTlb(Coins)
                        val forwardPayload = if (s.loadBit()) s.loadRef() else null
                        s.endParse()

                        return NftTransfer(
                            queryId,
                            newOwnerAddress,
                            excessesAddress,
                            customPayload,
                            forwardAmount,
                            forwardPayload
                        )
                    }

                    0x595f07bc -> {
                        val queryId = normalizeQueryId(s.loadUInt64())
                        val coins = s.loadTlb(Coins)
                        val responseDestination = s.loadTlb(AddrStd.tlbCodec())
                        var customPayload: JettonBurnCustomPayload? =
                            if (s.loadBit()) JettonBurnCustomPayload.CellPayload(s.loadRef()) else null
                        s.endParse()

                        if (params.encodeJettonBurnEthAddressAsHex
                            && customPayload != null && customPayload is JettonBurnCustomPayload.CellPayload
                            && customPayload.cell.bits.size == 20 && customPayload.cell.refs.isEmpty()
                        ) {
                            val cs = customPayload.cell.beginParse()
                            customPayload = JettonBurnCustomPayload.ByteArrayPayload(
                                cs.loadBits(20 * 8).toByteArray()
                            )
                            cs.endParse()
                        }

                        return JettonBurn(
                            queryId,
                            coins,
                            responseDestination,
                            customPayload
                        )
                    }

                    0x7258a69b -> {
                        val queryId = normalizeQueryId(s.loadUInt64())
                        val address = s.loadTlb(AddrStd.tlbCodec())
                        s.endParse()

                        return AddWhitelist(queryId, address)
                    }

                    0x1000 -> {
                        val queryId = normalizeQueryId(s.loadUInt64())
                        val coins = s.loadTlb(Coins)
                        s.endParse()

                        return SingleNominatorWithdraw(queryId, coins)
                    }

                    0x1001 -> {
                        val queryId = normalizeQueryId(s.loadUInt64())
                        val address = s.loadTlb(AddrStd.tlbCodec())
                        s.endParse()

                        return SingleNominatorChangeValidator(queryId, address)
                    }

                    0x47d54391 -> {
                        val queryId = normalizeQueryId(s.loadUInt64())
                        val appId = if (s.remainingBits > 0) s.loadUInt(64) else null
                        s.endParse()

                        return TonstakersDeposit(queryId, appId)
                    }

                    0x69fb306c -> {
                        val queryId = normalizeQueryId(s.loadUInt64())
                        val votingAddress = s.loadTlb(AddrStd.tlbCodec())
                        val expirationDate = s.loadUInt(48)
                        val vote = s.loadBit()
                        val needConfirmation = s.loadBit()
                        s.endParse()

                        return VoteForProposal(
                            queryId,
                            votingAddress,
                            expirationDate,
                            vote,
                            needConfirmation
                        )
                    }

                    0x4eb1f0f9 -> {
                        val queryId = normalizeQueryId(s.loadUInt64())
                        val key = s.loadBits(32 * 8).toByteArray()
                        if (key.contentEquals(dnsWalletKey)) {
                            if (s.remainingRefs > 0) {
                                val vs = s.loadRef().beginParse()
                                if (s.remainingBits > 0 && !params.disallowModification) {
                                    // tolerate the Maybe bit
                                    if (!s.loadBit()) throw Error("Incorrect change DNS record message")
                                }
                                s.endParse()

                                val type = vs.loadUInt(16).toInt()
                                if (type != 0x9fd3) {
                                    throw Error("Wrong DNS record type")
                                }

                                val address = vs.loadTlb(AddrStd.tlbCodec())
                                val flags = vs.loadUInt(8).toInt()
                                if (flags > 1) {
                                    throw Error("DNS wallet record must have flags 0 or 1")
                                }
                                val capabilities: DnsChangeCapabilities? =
                                    if ((flags and 1) > 0) DnsChangeCapabilities(isWallet = false) else null
                                if (capabilities != null) {
                                    while (vs.loadBit()) {
                                        val cap = vs.loadUInt(16).toInt()
                                        if (cap == 0x2177) {
                                            if (capabilities.isWallet && params.disallowModification) {
                                                throw Error("DNS change record message would be modified")
                                            }
                                            capabilities.isWallet = true
                                        } else {
                                            throw Error("Unknown DNS wallet record capability")
                                        }
                                    }
                                }

                                return ChangeDNSRecord(
                                    queryId = queryId,
                                    record = DNSRecord.Wallet(
                                        DNSWalletRecord(
                                            address,
                                            capabilities
                                        )
                                    )
                                )
                            } else {
                                if (s.remainingBits > 0 && !params.disallowModification) {
                                    // tolerate the Maybe bit
                                    if (s.loadBit()) throw Error("Incorrect change DNS record message")
                                }
                                s.endParse()

                                return ChangeDNSRecord(
                                    queryId = queryId,
                                    record = DNSRecord.Wallet(null)
                                )
                            }
                        } else {
                            if (s.remainingRefs > 0) {
                                val value = s.loadRef()
                                if (s.remainingBits > 0 && !params.disallowModification) {
                                    // tolerate the Maybe bit
                                    if (!s.loadBit()) throw Error("Incorrect change DNS record message")
                                }
                                s.endParse()

                                return ChangeDNSRecord(
                                    queryId = queryId,
                                    record = DNSRecord.Unknown(key, value)
                                )
                            } else {
                                if (s.remainingBits > 0 && !params.disallowModification) {
                                    // tolerate the Maybe bit
                                    if (s.loadBit()) throw Error("Incorrect change DNS record message")
                                }
                                s.endParse()

                                return ChangeDNSRecord(
                                    queryId = queryId,
                                    record = DNSRecord.Unknown(key, null)
                                )
                            }
                        }
                    }

                    0x8 -> {
                        val queryId = normalizeQueryId(s.loadUInt64())
                        val swapId = s.loadBits(32 * 8).toByteArray()
                        s.endParse()

                        return TokenBridgePaySwap(queryId, swapId)
                    }
                }
            } catch (e: Exception) {
                if (params.disallowUnsafe) {
                    throw e
                }
            }

            return Unsafe(cell)
        }
    }
}
