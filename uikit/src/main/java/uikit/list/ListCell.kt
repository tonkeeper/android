package uikit.list

interface ListCell {

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

        fun from(value: Int): Position {
            return when (value) {
                0 -> Position.FIRST
                1 -> Position.MIDDLE
                2 -> Position.LAST
                else -> Position.SINGLE
            }
        }

        fun from(value: String?): Position {
            return when (value) {
                "first", "0" -> Position.FIRST
                "middle", "1" -> Position.MIDDLE
                "last", "2" -> Position.LAST
                else -> Position.SINGLE
            }
        }
    }

    enum class Position(val value: Int) {
        FIRST(0),
        MIDDLE(1),
        LAST(2),
        SINGLE(3)
    }

    val position: Position

}