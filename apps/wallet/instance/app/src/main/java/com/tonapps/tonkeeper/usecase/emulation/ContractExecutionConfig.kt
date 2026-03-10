package com.tonapps.tonkeeper.usecase.emulation

import io.tonapi.models.BlockchainConfig

data class ContractExecutionConfig(
    val timeChunk: Int = TIME_CHUNK,
    val storageBitPrice: Long,
    val storageCellPrice: Long,
    val msgFwdBitPrice: Long,
    val msgFwdCellPrice: Long,
    val lumpPrice: Long,
    val firstFrac: Long,
    val gasPrice: Long
) {
    constructor(config: BlockchainConfig) : this(
        storageBitPrice = config._18?.storagePrices[0]?.bitPricePs ?: STORAGE_BIT_PRICE,
        storageCellPrice = config._18?.storagePrices[0]?.cellPricePs ?: STORAGE_CELL_PRICE,
        msgFwdBitPrice = config._25?.msgForwardPrices?.bitPrice ?: MSG_FWD_BIT_PRICE,
        msgFwdCellPrice = config._25?.msgForwardPrices?.cellPrice ?: MSG_FWD_CELL_PRICE,
        lumpPrice = config._25?.msgForwardPrices?.lumpPrice ?: LUMP_PRICE,
        firstFrac = config._25?.msgForwardPrices?.firstFrac ?: FIRST_FRAC,
        gasPrice = (config._21?.gasLimitsPrices?.gasPrice ?: GAS_PRICE) / TIME_CHUNK
    )

    companion object {
        private const val TIME_CHUNK = 65536

        const val STORAGE_BIT_PRICE: Long = 1
        const val STORAGE_CELL_PRICE: Long = 500
        const val MSG_FWD_BIT_PRICE: Long = 26214400
        const val MSG_FWD_CELL_PRICE: Long = 2621440000
        const val LUMP_PRICE: Long = 400000
        const val FIRST_FRAC: Long = 21845
        const val GAS_PRICE: Long = 26214400
    }
}
