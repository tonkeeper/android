package ui.components.moon.list

import androidx.compose.foundation.lazy.LazyListScope
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.MoonRetryCell

//fun LazyListScope.errorItem(onRetry: () -> Unit) {
//    item(key = "error", contentType = "error") {
//        MoonErrorCell(onRetry = onRetry)
//    }
//}

fun LazyListScope.loadingItem() {
    item(key = "loader", contentType = "loader") {
        MoonLoaderCell()
    }
}

fun LazyListScope.retryItem(
    text: String,
    buttonText: String,
    onRetry: () -> Unit,
) {
    item(key = "retry", contentType = "retry") {
        MoonRetryCell(message = text, buttonText = buttonText, onRetry = onRetry)
    }
}

//fun LazyListScope.notFoundItem() {
//    item(key = "not_found", contentType = "not_found") {
//        MoonEmptyCell()
//    }
//}
