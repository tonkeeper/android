package uikit.extensions

fun String.parseWords(): List<String> {
    val words = split(",", "\n", " ").map {
        it.trim()
    }.filter {
        it.isNotEmpty()
    }
    return words
}

fun String.isWords(): Boolean {
    return contains(",") || contains("\n") || contains(" ")
}
