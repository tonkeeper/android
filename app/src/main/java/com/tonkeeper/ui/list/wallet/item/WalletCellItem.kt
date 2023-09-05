package com.tonkeeper.ui.list.wallet.item

open class WalletCellItem(
    type: Int,
    val position: Position
): WalletItem(type) {

    companion object {
        fun getPosition(size: Int, index: Int): Position {
            if (1 >= size) {
                return Position.SINGLE
            }
            return when (index) {
                0 -> Position.FIRST
                size - 1 -> Position.LAST
                else -> Position.MIDDLE
            }
        }
    }

    enum class Position {
        FIRST,
        MIDDLE,
        LAST,
        SINGLE
    }

}