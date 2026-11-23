package com.tonapps.deposit.usecase.emulation

import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.API
import com.tonapps.blockchain.model.legacy.WalletEntity
import io.tonapi.models.Account
import io.tonapi.models.AccountStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ton.bitstring.BitString
import org.ton.cell.Cell
import java.math.BigDecimal
import kotlin.math.ceil

class EmulationContractExecution(private val api: API) {
    private suspend fun getConfig(network: TonNetwork): ContractExecutionConfig =
        withContext(Dispatchers.IO) {
            val config = api.blockchain(network).getBlockchainConfig()
            ContractExecutionConfig(config)
        }

    fun computeStorageFee(
        config: ContractExecutionConfig,
        version: WalletVersion,
        timeDelta: Long,
        isInited: Boolean
    ): Long {
        val (usedStorageBits, usedStorageCells) = if (!isInited) {
            103 to 1
        } else {
            val figures = figures(version)
            figures.storageBits to figures.storageCells
        }

        val used =
            usedStorageBits * config.storageBitPrice + usedStorageCells * config.storageCellPrice
        return ceil(((used * timeDelta) / config.timeChunk).toDouble()).toLong()
    }


    private fun computeGasFee(
        config: ContractExecutionConfig, version: WalletVersion, outMsgsCount: Int
    ): Long {
        val figures = figures(version)
        val gasUsed = figures.baseGas + figures.gasPerMessage * outMsgsCount

        return gasUsed * config.gasPrice
    }

    private fun computeMsgFwdFee(
        config: ContractExecutionConfig,
        msgBits: Int,
        msgCells: Int
    ): Long {
        val bitsPrice = config.msgFwdBitPrice * msgBits
        val cellsPrice = config.msgFwdCellPrice * msgCells
        return config.lumpPrice + ceil((bitsPrice + cellsPrice).toDouble() / config.timeChunk).toInt()
    }

    fun computeImportFee(
        config: ContractExecutionConfig, msgBits: Int, msgCells: Int
    ): Long {
        return config.lumpPrice + ceil(
            ((config.msgFwdBitPrice * msgBits + config.msgFwdCellPrice * msgCells) / config.timeChunk).toDouble()
        ).toLong()
    }

    private fun countBitsAndCellsInMsg(msg: Cell, hashes: MutableSet<BitString>): Pair<Int, Int> {
        val hash = msg.hash()
        if (!hashes.add(hash)) {
            return 0 to 0
        }

        var cells = 1
        var bits = msg.bits.size

        for (ref in msg.refs) {
            val (innerBits, innerCells) = countBitsAndCellsInMsg(ref, hashes)
            bits += innerBits
            cells += innerCells
        }

        return bits to cells
    }

    suspend fun computeFee(
        wallet: WalletEntity,
        account: Account,
        inMsg: Cell,
        outMsgs: List<Cell>
    ): Coins =
        withContext(
            Dispatchers.IO
        ) {
            val config = getConfig(wallet.network)

            val nowTimestamp = api.getServerTime(wallet.network)
            val isInited =
                account.status != AccountStatus.uninit && account.status != AccountStatus.nonexist
            val timeDelta = nowTimestamp - account.lastActivity

            var msgBits = 0
            var msgCells = 0
            val inMsgHashes = mutableSetOf<BitString>()
            for (ref in inMsg.refs) {
                val (bits, cells) = countBitsAndCellsInMsg(ref, inMsgHashes)
                msgBits += bits
                msgCells += cells
            }

            var msgFwdFee: Long = 0
            for (outMsg in outMsgs) {
                var fwdMsgBits = 0
                var fwdMsgCells = 0
                val fwdMsgHashes = mutableSetOf<BitString>()
                for (ref in outMsg.refs) {
                    val (bits, cells) = countBitsAndCellsInMsg(ref, fwdMsgHashes)
                    fwdMsgBits += bits
                    fwdMsgCells += cells
                }
                msgFwdFee += computeMsgFwdFee(config, fwdMsgBits, fwdMsgCells)
            }

            val storageFee = computeStorageFee(config, wallet.version, timeDelta, isInited)
            val gasFee = computeGasFee(config, wallet.version, outMsgs.size)
            val importFee = computeImportFee(config, msgBits, msgCells)

            val base = BigDecimal(storageFee + msgFwdFee + gasFee + importFee)

            Coins.of(((base * GAS_SAFETY_MULTIPLIER) / GAS_SAFETY_MULTIPLIER_DENOMINATOR).toLong())
        }

    suspend fun computeRemoveExtensionFee(
        wallet: WalletEntity,
        inMsg: Cell,
        outMsgs: List<Cell>
    ): Coins =
        withContext(
            Dispatchers.IO
        ) {
            val config = getConfig(wallet.network)

            var msgBits = 0
            var msgCells = 0
            val inMsgHashes = mutableSetOf<BitString>()
            for (ref in inMsg.refs) {
                val (bits, cells) = countBitsAndCellsInMsg(ref, inMsgHashes)
                msgBits += bits
                msgCells += cells
            }

            var msgFwdFee: Long = 0
            for (outMsg in outMsgs) {
                var fwdMsgBits = 0
                var fwdMsgCells = 0
                val fwdMsgHashes = mutableSetOf<BitString>()
                for (ref in outMsg.refs) {
                    val (bits, cells) = countBitsAndCellsInMsg(ref, fwdMsgHashes)
                    fwdMsgBits += bits
                    fwdMsgCells += cells
                }
                msgFwdFee += computeMsgFwdFee(config, fwdMsgBits, fwdMsgCells)
            }

            val gasUsed = when (wallet.version) {
                WalletVersion.V4R2 -> 6615
                WalletVersion.V5BETA -> 8444
                WalletVersion.V5R1 -> 8444
                else -> throw IllegalArgumentException("Unknown wallet version: $wallet.version")
            }

            val importFee = computeImportFee(config, msgBits, msgCells)
            val gasFee = gasUsed * config.gasPrice

            val base = BigDecimal(msgFwdFee + gasFee + importFee)

            Coins.of(((base * GAS_SAFETY_MULTIPLIER) / GAS_SAFETY_MULTIPLIER_DENOMINATOR).toLong())
        }


    fun hasLocalEstimate(version: WalletVersion): Boolean = version in FIGURES

    private fun figures(version: WalletVersion): Figures {
        return FIGURES[version] ?: throw IllegalArgumentException("Unknown wallet version: $version")
    }

    private data class Figures(
        val storageBits: Int,
        val storageCells: Int,
        val baseGas: Int,
        val gasPerMessage: Int,
    )

    companion object {
        private val FIGURES = mapOf(
            WalletVersion.V3R1 to Figures(storageBits = 1163, storageCells = 3, baseGas = 2275, gasPerMessage = 642),
            WalletVersion.V3R2 to Figures(storageBits = 1283, storageCells = 3, baseGas = 2352, gasPerMessage = 642),
            WalletVersion.V4R2 to Figures(storageBits = 1315, storageCells = 3, baseGas = 2666, gasPerMessage = 642),
            WalletVersion.V5BETA to Figures(storageBits = 749, storageCells = 3, baseGas = 3079, gasPerMessage = 328),
            WalletVersion.V5R1 to Figures(storageBits = 5020, storageCells = 22, baseGas = 4222, gasPerMessage = 717),
        )
        private val GAS_SAFETY_MULTIPLIER = BigDecimal(105)
        private val GAS_SAFETY_MULTIPLIER_DENOMINATOR = BigDecimal(100)
    }

}