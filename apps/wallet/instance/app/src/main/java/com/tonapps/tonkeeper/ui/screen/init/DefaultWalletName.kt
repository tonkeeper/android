package com.tonapps.tonkeeper.ui.screen.init

object DefaultWalletName {

    fun suggest(base: String, existingLabels: List<String>): String {
        val name = base.trim()
        if (name.isEmpty()) {
            return base
        }

        var maxIndex = 0
        for (label in existingLabels) {
            val index = matchIndex(name, label.trim()) ?: continue
            if (index > maxIndex) {
                maxIndex = index
            }
        }

        if (maxIndex in 1 until Int.MAX_VALUE) {
            return "$name ${maxIndex + 1}"
        }
        return name
    }

    private fun matchIndex(name: String, label: String): Int? {
        if (label.equals(name, ignoreCase = true)) {
            return 1
        }

        val prefix = "$name "
        if (!label.startsWith(prefix, ignoreCase = true)) {
            return null
        }

        val suffix = label.substring(prefix.length)
        if (suffix.isEmpty() || suffix.first() == '0' || suffix.any { it !in '0'..'9' }) {
            return null
        }
        return suffix.toIntOrNull()
    }
}
