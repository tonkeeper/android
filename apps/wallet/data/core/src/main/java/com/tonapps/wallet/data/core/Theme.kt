package com.tonapps.wallet.data.core

data class Theme(
    val key: String,
    val resId: Int,
    val light: Boolean,
    val title: String
) {

    val isSystem: Boolean
        get() = key == "system" || key == "auto" || resId == 0

    companion object {

        private val supportedThemes = mutableListOf<Theme>()

        fun clear() {
            supportedThemes.clear()
        }

        fun getByKey(key: String): Theme {
            return supportedThemes.firstOrNull { it.key == key } ?: supportedThemes.first()
        }

        fun getByResId(resId: Int): Theme {
            return supportedThemes.firstOrNull { it.resId == resId } ?: supportedThemes.first()
        }

        fun add(key: String, resId: Int, light: Boolean = false, title: String, ) {
            supportedThemes.add(Theme(key, resId, light, title))
        }

        fun getSupported(): List<Theme> {
            return supportedThemes
        }


    }
}