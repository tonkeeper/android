package ui.components.details

import androidx.compose.runtime.Stable
import ui.ComposeIcon

@Stable
data class UiDetails(
    val rows: List<Row> = emptyList(),
) {

    val itemCount = rows.size

    fun get(index: Int) = rows[index]

    data class Row(
        val id: String,
        val key: CharSequence,
        val value: CharSequence,
        val iconLeft: ComposeIcon? = null,
        val secondaryValue: CharSequence? = null,
        val clickable: Boolean = false,
        val spoiler: Boolean = false,
    )
}