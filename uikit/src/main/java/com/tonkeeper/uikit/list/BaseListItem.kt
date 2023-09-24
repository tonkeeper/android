package com.tonkeeper.uikit.list

open class BaseListItem(
    val type: Int = NOT_TYPE
) {

    interface Cell {

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

        val position: Position
    }


    companion object {
        const val NOT_TYPE = 0
    }
}